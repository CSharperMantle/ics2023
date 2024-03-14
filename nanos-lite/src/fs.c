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
  size_t open_offset;
} Finfo;

enum { FD_STDIN = 0, FD_STDOUT, FD_STDERR, FD_FB };

static size_t serial_write(const void *buf, size_t offset, size_t len) {
  (void)offset;

  for (size_t i = 0; i < len; i++) {
    putchar(((char *)buf)[i]);
  }
  return len;
}

static size_t file_read(void *buf, size_t offset, size_t len) {
  return ramdisk_read(buf, offset, len);
}

static size_t file_write(const void *buf, size_t offset, size_t len) {
  return ramdisk_write(buf, offset, len);
}

/* This is the information about all files in disk. */
static Finfo file_table[] __attribute__((used)) = {
    [FD_STDIN] = {"stdin",  0, 0, NULL, NULL        },
    [FD_STDOUT] = {"stdout", 0, 0, NULL, serial_write},
    [FD_STDERR] = {"stderr", 0, 0, NULL, serial_write},
#include "files.h"
};

void init_fs() {
  // TODO: initialize the size of /dev/fb
}

int fs_open(const char *pathname, int flags, int mode) {
  // TODO: mode
  (void)mode;

  for (size_t i = FD_STDERR + 1; i < ARRLEN(file_table); i++) {
    if (!strcmp(pathname, file_table[i].name)) {
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
        default: return -1;
      }
      file_table[i].open_offset = 0;
      return i;
    }
  }
  return -1;
}

ssize_t fs_read(int fd, void *buf, size_t len) {
  Finfo *const finfo = file_table + fd;
  if (fd < 0 || fd >= ARRLEN(file_table) || finfo->read == NULL) {
    return -1;
  }
  len = MIN(finfo->size, finfo->open_offset + len) - finfo->open_offset;
  size_t nbytes_done = finfo->read(buf, finfo->disk_offset + finfo->open_offset, len);
  finfo->open_offset += nbytes_done;
  return nbytes_done;
}

ssize_t fs_write(int fd, const void *buf, size_t len) {
  Finfo *const finfo = file_table + fd;
  if (fd < 0 || fd >= ARRLEN(file_table) || finfo->write == NULL) {
    return -1;
  }

  if (fd > FD_STDERR) {
    len = MIN(finfo->size, finfo->open_offset + len) - finfo->open_offset;
  }
  size_t nbytes_done = finfo->write(buf, finfo->disk_offset + finfo->open_offset, len);
  finfo->open_offset += nbytes_done;
  return nbytes_done;
}

off_t fs_lseek(int fd, off_t offset, int whence) {
  if (fd < 0 || fd >= ARRLEN(file_table)) {
    return -1;
  }
  if (fd == FD_STDIN || fd == FD_STDOUT || fd == FD_STDERR) {
    return -1;
  }
  switch (whence) {
    case SEEK_SET: file_table[fd].open_offset = offset; break;
    case SEEK_CUR: file_table[fd].open_offset += offset; break;
    case SEEK_END: file_table[fd].open_offset = file_table[fd].size + offset; break;
    default: return -1;
  }
  return file_table[fd].open_offset;
}

int fs_close(int fd) {
  Finfo *const finfo = file_table + fd;
  if (fd < 0 || fd >= ARRLEN(file_table)) {
    return -1;
  }
  finfo->read = NULL;
  finfo->write = NULL;
  finfo->open_offset = 0;
  return 0;
}
