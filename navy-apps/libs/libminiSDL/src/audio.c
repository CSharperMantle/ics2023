#include <NDL.h>
#include <assert.h>
#include <sdl-audio.h>
#include <sdl-timer.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

SdlAudioCallback_t audio_callback;

int SDL_OpenAudio(SDL_AudioSpec *desired, SDL_AudioSpec *obtained) {
  if (desired == NULL) {
    return -1;
  }
  NDL_OpenAudio(desired->freq, desired->channels, desired->samples);
  if (obtained != NULL) {
    memcpy(obtained, desired, sizeof(SDL_AudioSpec));
  }
  const size_t sample_size = desired->format == AUDIO_S16 ? sizeof(uint16_t) : sizeof(uint8_t);
  audio_callback = (SdlAudioCallback_t){
      .callback = desired->callback,
      .userdata = desired->userdata,
      .last_called = 0,
      .interval = desired->samples / desired->freq / 1000,
      .buf_size = desired->samples * desired->channels * sample_size,
      .buf = NULL,
      .valid = true,
      .paused = false,
      .locked = false,
  };

  audio_callback.buf = (uint8_t *)malloc(sizeof(uint8_t) * audio_callback.buf_size);
  if (audio_callback.buf == NULL) {
    return -1;
  }

  return 0;
}

void SDL_CloseAudio(void) {
  NDL_CloseAudio();
  audio_callback.valid = false;
  if (audio_callback.buf != NULL) {
    free(audio_callback.buf);
    audio_callback.buf = NULL;
  }
}

void SDL_PauseAudio(int pause_on) {
  audio_callback.paused = !!pause_on;
}

#define MIN(x_, y_) ((x_) <= (y_) ? (x_) : (y_))
#define MAX(x_, y_) ((x_) >= (y_) ? (x_) : (y_))

void SDL_MixAudio(uint8_t *dst, uint8_t *src, uint32_t len, int volume) {
  int16_t *const buf_dst = (int16_t *)dst;
  const int16_t *const buf_src = (const int16_t *)src;
  len /= 2;

  for (size_t i = 0; i < len; i++) {
    const int16_t sample_1 = (int16_t)(buf_src[i] * volume / SDL_MIX_MAXVOLUME);
    const int16_t sample_2 = buf_dst[i];
    const int32_t sample_ovf = sample_1 + sample_2;
    buf_dst[i] = (int16_t)MAX(MIN(sample_ovf, INT16_MAX), INT16_MIN);
  }
}

typedef struct RiffHeader_ {
  uint8_t group_id[4];
  uint32_t size;
  uint8_t riff_type[4];
} RiffHeader_t;

typedef struct RiffChunkHeader_ {
  uint8_t chunk_id[4];
  uint32_t chunk_size;
} RiffChunkHeader_t;

typedef struct WavFmtChunk_ {
  uint16_t format_tag;
  uint16_t channels;
  uint32_t samples_per_sec;
  uint32_t avg_bytes_per_sec;
  uint16_t block_align;
  uint16_t bits_per_sample;
} WavFmtChunk_t;

SDL_AudioSpec *
SDL_LoadWAV(const char *file, SDL_AudioSpec *spec, uint8_t **audio_buf, uint32_t *audio_len) {
  SDL_AudioSpec s = {0};
  FILE *f = fopen(file, "rb");
  rewind(f);

  RiffHeader_t riff_hdr;
  fread(&riff_hdr, sizeof(RiffHeader_t), 1, f);
  if (memcmp(&riff_hdr.group_id, "RIFF", sizeof(riff_hdr.group_id))
      || memcmp(&riff_hdr.riff_type, "WAVE", sizeof(riff_hdr.riff_type))) {
    goto L_FAIL;
  }

  long cursor = ftell(f);

  fseek(f, 0, SEEK_END);
  const long size = ftell(f);
  fseek(f, cursor, SEEK_SET);

  while (cursor < size) {
    RiffChunkHeader_t chunk_hdr;
    fread(&chunk_hdr, sizeof(RiffChunkHeader_t), 1, f);

    if (!memcmp(&chunk_hdr.chunk_id, "fmt ", sizeof(chunk_hdr.chunk_id))) {
      WavFmtChunk_t fmt;
      fread(&fmt, sizeof(WavFmtChunk_t), 1, f);

      assert(fmt.format_tag == 1);
      assert(fmt.bits_per_sample == 16);

      s.channels = fmt.channels;
      s.freq = fmt.samples_per_sec;
      s.format = AUDIO_S16SYS;
    } else if (!memcmp(&chunk_hdr.chunk_id, "data", sizeof(chunk_hdr.chunk_id))) {
      uint8_t *buf = (uint8_t *)malloc(sizeof(uint8_t) * chunk_hdr.chunk_size);
      fread(buf, chunk_hdr.chunk_size, sizeof(uint8_t), f);

      *audio_len = s.size = chunk_hdr.chunk_size;
      *audio_buf = buf;
    } else {
      fseek(f, chunk_hdr.chunk_size, SEEK_CUR);
    }

    cursor = ftell(f);
  }

  s.samples = s.size / ((sizeof(uint16_t) / sizeof(uint8_t)) * s.channels);

  fclose(f);
  *spec = s;
  return spec;

L_FAIL:
  fclose(f);
  return NULL;
}

void SDL_FreeWAV(uint8_t *audio_buf) {
  free(audio_buf);
}

void SDL_LockAudio(void) {
  audio_callback.locked = true;
}

void SDL_UnlockAudio(void) {
  audio_callback.locked = false;
}

void sdl_schedule_audio_callback(void) {
  if (!audio_callback.valid || audio_callback.paused || audio_callback.locked) {
    return;
  }
  if (NDL_GetTicks() - audio_callback.last_called < audio_callback.interval) {
    return;
  }

  const size_t size = audio_callback.buf_size;
  memset(audio_callback.buf, 0, sizeof(uint8_t) * size);

  audio_callback.callback(audio_callback.userdata, audio_callback.buf, size);
  NDL_PlayAudio(audio_callback.buf, size);
  audio_callback.last_called = NDL_GetTicks();
}
