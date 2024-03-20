#ifndef __FS_H__
#define __FS_H__

#include <common.h>
#include <sys/types.h>

#ifndef SEEK_SET
enum { SEEK_SET = 0, SEEK_CUR, SEEK_END };
#endif

void init_fs(void);
int fs_open(const char *pathname, int flags, int mode);
ssize_t fs_read(int fd, void *buf, size_t len);
ssize_t fs_write(int fd, const void *buf, size_t len);
off_t fs_lseek(int fd, off_t offset, int whence);
int fs_close(int fd);

#endif
