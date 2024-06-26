#ifndef DEVICE_H_INCLUDED_
#define DEVICE_H_INCLUDED_

#include <common.h>

size_t serial_write(const void *buf, size_t offset, size_t len);
size_t events_read(void *buf, size_t offset, size_t len);
size_t fb_write(const void *buf, size_t offset, size_t len);
size_t dispinfo_read(void *buf, size_t offset, size_t len);
size_t sb_write(const void *buf, size_t offset, size_t len);
size_t sbctl_read(void *buf, size_t offset, size_t len);
size_t sbctl_write(const void *buf, size_t offset, size_t len);
size_t ioe_pt_read(void *buf, size_t offset, size_t len);
size_t ioe_pt_write(const void *buf, size_t offset, size_t len);
void init_device(void);

#endif
