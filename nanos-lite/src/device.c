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

size_t fb_write(const void *buf, size_t offset, size_t len) {
  io_write(AM_GPU_MEMCPY, offset, (void *)buf, len);
  io_write(AM_GPU_FBDRAW, 0, 0, NULL, 0, 0, true);
  return len;
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

size_t sb_write(const void *buf, size_t offset, size_t len) {
  (void)offset;

  const AM_AUDIO_CONFIG_T conf = io_read(AM_AUDIO_CONFIG);
  if (!conf.present) {
    return 0;
  }

  AM_AUDIO_STATUS_T state;
  do {
    state = io_read(AM_AUDIO_STATUS);
  } while ((size_t)conf.bufsize < (size_t)state.count + len);

  const Area buf_area = {
      .start = (void *)buf,
      .end = (void *)buf + len,
  };
  io_write(AM_AUDIO_PLAY, buf_area);
  return len;
}

size_t sbctl_read(void *buf, size_t offset, size_t len) {
  (void)offset;

  len = MIN(len, sizeof(int));
  const AM_AUDIO_STATUS_T state = io_read(AM_AUDIO_STATUS);
  memcpy(buf, &state.count, len);
  return len;
}

size_t sbctl_write(const void *buf, size_t offset, size_t len) {
  (void)offset;

  struct {
    int freq;
    int channels;
    int samples;
  } __attribute__((packed)) data;
  len = MIN(len, sizeof(data));
  memcpy(&data, buf, len);
  io_write(AM_AUDIO_CTRL, data.freq, data.channels, data.samples);
  return len;
}

size_t ioe_pt_read(void *buf, size_t offset, size_t len) {
  (void)len;

  ioe_read(offset, buf);
  return 0;
}

size_t ioe_pt_write(const void *buf, size_t offset, size_t len) {
  (void)len;

  ioe_write(offset, (void *)buf);
  return len;
}

void init_device() {
  Log("Initializing devices...");
  ioe_init();
}
