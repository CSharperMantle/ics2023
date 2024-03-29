#include <device.h>
#include <errno.h>
#include <fcntl.h>
#include <fs.h>
#include <ramdisk.h>
#include <string.h>
#include <sys/types.h>

typedef size_t (*ReadFn)(void *buf, size_t offset, size_t len);
typedef size_t (*WriteFn)(const void *buf, size_t offset, size_t len);

typedef struct {
  char *name;
  size_t size;
  size_t disk_offset;
  ReadFn read;
  WriteFn write;
  bool unseekable;
  size_t open_offset;
} Finfo;

enum {
  FD_STDIN = 0,
  FD_STDOUT,
  FD_STDERR,
  FD_EVENTS,
  FD_FB,
  FD_DISPINFO,
  FD_SB,
  FD_SBCTL,
  FD_IOE_PT,
};

static const int LIST_FD_SPECIAL[] = {
    FD_STDIN,
    FD_STDOUT,
    FD_STDERR,
    FD_EVENTS,
    FD_FB,
    FD_DISPINFO,
    FD_SB,
    FD_SBCTL,
    FD_IOE_PT,
};

static size_t file_read(void *buf, size_t offset, size_t len) {
  return ramdisk_read(buf, offset, len);
}

static size_t file_write(const void *buf, size_t offset, size_t len) {
  return ramdisk_write(buf, offset, len);
}

/* This is the information about all files in disk. */
static Finfo file_table[] __attribute__((used)) = {
    [FD_STDIN] = {"stdin",          0, 0, NULL,          NULL,         true },
    [FD_STDOUT] = {"stdout",         0, 0, NULL,          serial_write, true },
    [FD_STDERR] = {"stderr",         0, 0, NULL,          serial_write, true },
    [FD_EVENTS] = {"/dev/events",    0, 0, events_read,   NULL,         true },
    [FD_FB] = {"/dev/fb",        0, 0, NULL,          fb_write,     false},
    [FD_DISPINFO] = {"/proc/dispinfo", 0, 0, dispinfo_read, NULL,         true },
    [FD_SB] = {"/dev/sb",        0, 0, NULL,          sb_write,     true },
    [FD_SBCTL] = {"/dev/sbctl",     0, 0, sbctl_read,    sbctl_write,  true },
    [FD_IOE_PT] = {"/dev/ioe_pt",    0, 0, ioe_pt_read,   ioe_pt_write, false},
#include "files.h"
};

static bool is_special(int fd) {
  for (size_t i = 0; i < ARRLEN(LIST_FD_SPECIAL); i++) {
    if (LIST_FD_SPECIAL[i] == fd) {
      return true;
    }
  }
  return false;
}

void init_fs(void) {
  const AM_GPU_CONFIG_T fb_state = io_read(AM_GPU_CONFIG);
  if (fb_state.present) {
    file_table[FD_FB].size = fb_state.vmemsz;
  }

  const AM_AUDIO_CONFIG_T sb_state = io_read(AM_AUDIO_CONFIG);
  if (sb_state.present) {
    file_table[FD_SB].size = sb_state.bufsize;
  }

  file_table[FD_IOE_PT].size = -1;
}

int fs_open(const char *pathname, int flags, int mode) {
  // TODO: mode
  (void)mode;

  for (size_t i = 0; i < ARRLEN(file_table); i++) {
    if (!strcmp(pathname, file_table[i].name)) {
      if (!is_special(i)) {
        const uint8_t access = flags & 0b11u;
        switch (access) {
          case O_RDONLY:
            file_table[i].read = file_read;
            file_table[i].write = NULL;
            break;
          case O_WRONLY:
            file_table[i].read = NULL;
            file_table[i].write = file_write;
            break;
          case O_RDWR:
            file_table[i].read = file_read;
            file_table[i].write = file_write;
            break;
          default: return -EACCES;
        }
        file_table[i].open_offset = 0;
      } else if (file_table[i].size != 0) {
        file_table[i].open_offset = 0;
      }
      return i;
    }
  }
  return -ENOENT;
}

ssize_t fs_read(int fd, void *buf, size_t len) {
  Finfo *const finfo = file_table + fd;
  if (fd < 0 || fd >= ARRLEN(file_table) || finfo->read == NULL) {
    return -EBADF;
  }
  if (!is_special(fd)) {
    len = MIN(finfo->size, finfo->open_offset + len) - finfo->open_offset;
  }
  size_t nbytes_done = finfo->read(buf, finfo->disk_offset + finfo->open_offset, len);
  finfo->open_offset += nbytes_done;
  return nbytes_done;
}

ssize_t fs_write(int fd, const void *buf, size_t len) {
  Finfo *const finfo = file_table + fd;
  if (fd < 0 || fd >= ARRLEN(file_table) || finfo->write == NULL) {
    return -EBADF;
  }

  if (!is_special(fd)) {
    len = MIN(finfo->size, finfo->open_offset + len) - finfo->open_offset;
  }
  size_t nbytes_done = finfo->write(buf, finfo->disk_offset + finfo->open_offset, len);
  finfo->open_offset += nbytes_done;
  return nbytes_done;
}

off_t fs_lseek(int fd, off_t offset, int whence) {
  if (fd < 0 || fd >= ARRLEN(file_table) || (is_special(fd) && file_table[fd].unseekable)) {
    return -EBADF;
  }
  switch (whence) {
    case SEEK_SET: file_table[fd].open_offset = offset; break;
    case SEEK_CUR: file_table[fd].open_offset += offset; break;
    case SEEK_END: file_table[fd].open_offset = file_table[fd].size + offset; break;
    default: return -EINVAL;
  }
  return file_table[fd].open_offset;
}

int fs_close(int fd) {
  Finfo *const finfo = file_table + fd;
  if (fd < 0 || fd >= ARRLEN(file_table)) {
    return -EBADF;
  }
  if (!is_special(fd)) {
    finfo->read = NULL;
    finfo->write = NULL;
  }
  finfo->open_offset = 0;
  return 0;
}
