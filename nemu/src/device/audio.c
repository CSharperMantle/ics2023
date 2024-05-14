/***************************************************************************************
 * Copyright (c) 2014-2022 Zihao Yu, Nanjing University
 *
 * NEMU is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 *
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
 * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
 * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 *
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/

#include <SDL2/SDL.h>
#include <SDL2/SDL_audio.h>
#include <common.h>
#include <device/map.h>
#include <string.h>

enum { REG_FREQ = 0, REG_CHANNELS, REG_SAMPLES, REG_SBUF_SIZE, REG_INIT, REG_COUNT, NR_REG_ };

static bool audio_opened = false;

static uint8_t *sbuf = NULL;
static size_t sbuf_pos = 0;
static size_t sbuf_count = 0;
static uint32_t *audio_base = NULL;

typedef struct AudioSpec_ {
  uint32_t freq;
  uint32_t channels;
  uint32_t samples;
} AudioSpec_t;

static AudioSpec_t audio_spec;

static void callback_play(void *user_data, uint8_t *stream, int len) {
  memset(stream, 0, len);
  const size_t chunk_len = (size_t)len > sbuf_count ? sbuf_count : (size_t)len;
  if ((sbuf_pos + chunk_len) > CONFIG_SB_SIZE) {
    const uint32_t len_rem = CONFIG_SB_SIZE - sbuf_pos;
    SDL_MixAudio(stream, sbuf + sbuf_pos, len_rem, SDL_MIX_MAXVOLUME);
    SDL_MixAudio(stream + len_rem, sbuf, chunk_len - len_rem, SDL_MIX_MAXVOLUME);
  } else {
    SDL_MixAudio(stream, sbuf + sbuf_pos, chunk_len, SDL_MIX_MAXVOLUME);
  }
  sbuf_pos = (sbuf_pos + chunk_len) % CONFIG_SB_SIZE;
  sbuf_count -= chunk_len;
}

static void do_sdl_audio_init(void) {
  sbuf_pos = 0;
  sbuf_count = 0;

  SDL_AudioSpec want = {
      .freq = audio_spec.freq,
      .channels = audio_spec.channels,
      .samples = audio_spec.samples,
      .format = AUDIO_S16SYS,
      .userdata = NULL,
      .callback = callback_play,
  };

  if (SDL_InitSubSystem(SDL_INIT_AUDIO)) {
    Warn("%s", SDL_GetError());
  }
  if (SDL_OpenAudio(&want, NULL)) {
    Warn("%s", SDL_GetError());
  }
  SDL_PauseAudio(0);

  audio_opened = true;
}

static void do_sdl_audio_shutdown(void) {
  SDL_PauseAudio(1);
  SDL_CloseAudio();
  SDL_QuitSubSystem(SDL_INIT_AUDIO);
  audio_opened = false;
}

static void audio_io_handler(uint32_t offset, int len, bool is_write) {
  assert(len == 4);
  switch (offset / sizeof(uint32_t)) {
    case REG_FREQ:
      if (is_write) {
        audio_spec.freq = audio_base[REG_FREQ];
      }
      break;
    case REG_CHANNELS:
      if (is_write) {
        audio_spec.channels = audio_base[REG_CHANNELS];
      }
      break;
    case REG_SAMPLES:
      if (is_write) {
        audio_spec.samples = audio_base[REG_SAMPLES];
      }
      break;
    case REG_SBUF_SIZE:
      if (!is_write) {
        audio_base[REG_SBUF_SIZE] = CONFIG_SB_SIZE;
      }
      break;
    case REG_INIT:
      if (is_write && audio_base[REG_INIT]) {
        if (audio_opened) {
          do_sdl_audio_shutdown();
        }
        do_sdl_audio_init();
        audio_base[REG_INIT] = 0;
      }
      break;
    case REG_COUNT:
      if (!is_write) {
        audio_base[REG_COUNT] = (uint32_t)sbuf_count;
      }
      break;
    default: panic("do not support offset = %d", offset);
  }
}

static void audio_sbuf_handler(uint32_t offset, int len, bool is_write) {
  if (likely(is_write)) {
    sbuf_count += len;
  }
}

void init_audio() {
  const uint32_t space_size = sizeof(uint32_t) * NR_REG_;
  audio_base = (uint32_t *)new_space(space_size);
#ifdef CONFIG_HAS_PORT_IO
  add_pio_map("audio", CONFIG_AUDIO_CTL_PORT, audio_base, space_size, audio_io_handler);
#else
  add_mmio_map("audio", CONFIG_AUDIO_CTL_MMIO, audio_base, space_size, audio_io_handler);
#endif

  sbuf = (uint8_t *)new_space(CONFIG_SB_SIZE);
  add_mmio_map("audio-sbuf", CONFIG_SB_ADDR, sbuf, CONFIG_SB_SIZE, audio_sbuf_handler);
}
