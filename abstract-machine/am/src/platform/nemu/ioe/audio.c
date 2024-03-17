#include <am.h>
#include <nemu.h>
#include <stdint.h>

#define AUDIO_ADDR_REG_FREQ      (AUDIO_ADDR + 0x00)
#define AUDIO_ADDR_REG_CHANNELS  (AUDIO_ADDR + 0x04)
#define AUDIO_ADDR_REG_SAMPLES   (AUDIO_ADDR + 0x08)
#define AUDIO_ADDR_REG_SBUF_SIZE (AUDIO_ADDR + 0x0c)
#define AUDIO_ADDR_REG_INIT      (AUDIO_ADDR + 0x10)
#define AUDIO_ADDR_REG_COUNT     (AUDIO_ADDR + 0x14)

static size_t sbuf_pos = 0;
static uint32_t sbuf_size = 0;

void __am_audio_init(void) {
  sbuf_pos = 0;
  sbuf_size = inl(AUDIO_ADDR_REG_SBUF_SIZE);
}

void __am_audio_config(AM_AUDIO_CONFIG_T *cfg) {
  cfg->present = true;
  cfg->bufsize = sbuf_size;
}

void __am_audio_ctrl(AM_AUDIO_CTRL_T *ctrl) {
  outl(AUDIO_ADDR_REG_FREQ, ctrl->freq);
  outl(AUDIO_ADDR_REG_CHANNELS, ctrl->channels);
  outl(AUDIO_ADDR_REG_SAMPLES, ctrl->samples);

  outl(AUDIO_ADDR_REG_INIT, 1);
}

void __am_audio_status(AM_AUDIO_STATUS_T *stat) {
  stat->count = inl(AUDIO_ADDR_REG_COUNT);
}

void __am_audio_play(AM_AUDIO_PLAY_T *ctl) {
  const uint16_t *const buf = ctl->buf.start;
  const uint32_t len = (uint16_t *)(ctl->buf.end) - buf;

  for (size_t i = 0; i < len; i++) {
    outw(AUDIO_SBUF_ADDR + sbuf_pos, buf[i]);
    sbuf_pos = (sbuf_pos + 2) % sbuf_size;
  }
}
