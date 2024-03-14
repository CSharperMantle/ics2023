#include <common.h>
#include <device.h>
#include <stdio.h>

#if defined(MULTIPROGRAM) && !defined(TIME_SHARING)
#define MULTIPROGRAM_YIELD() yield()
#else
#define MULTIPROGRAM_YIELD()
#endif

#define NAME(key) [AM_KEY_##key] = #key,

static const char *KEYNAME[256] = {[AM_KEY_NONE] = "NONE", AM_KEYS(NAME)};

size_t serial_write(const void *buf, size_t offset, size_t len) {
  (void)offset;
  for (size_t i = 0; i < len; i++) {
    putchar(((char *)buf)[i]);
  }
  return len;
}

size_t events_read(void *buf, size_t offset, size_t len) {
  (void)offset;
  const AM_INPUT_KEYBRD_T state = io_read(AM_INPUT_KEYBRD);
  if (state.keycode == AM_KEY_NONE) {
    return 0;
  }
  int actual_len =
      snprintf(buf, len, "k%c %s\n", state.keydown ? 'd' : 'u', KEYNAME[state.keycode]);
  return actual_len;
}

size_t dispinfo_read(void *buf, size_t offset, size_t len) {
  (void)offset;
  const AM_GPU_CONFIG_T state = io_read(AM_GPU_CONFIG);
  if (!state.present) {
    return 0;
  }
  int actual_len = snprintf(buf, len, "WIDTH:%d\nHEIGHT:%d\n", state.width, state.height);
  return actual_len;
}

size_t fb_write(const void *buf, size_t offset, size_t len) {
  io_write(AM_GPU_MEMCPY, offset, (void *)buf, len);
  io_write(AM_GPU_FBDRAW, 0, 0, NULL, 0, 0, true);
  return len;
}

void init_device() {
  Log("Initializing devices...");
  ioe_init();
}
