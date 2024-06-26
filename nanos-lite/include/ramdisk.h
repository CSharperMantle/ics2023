#ifndef RAMDISK_H_INCLUDED_
#define RAMDISK_H_INCLUDED_

#include <common.h>

void init_ramdisk(void);
size_t ramdisk_read(void *buf, size_t offset, size_t len);
size_t ramdisk_write(const void *buf, size_t offset, size_t len);
size_t get_ramdisk_size(void);

#endif
