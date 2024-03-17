#include <NDL.h>
#include <assert.h>
#include <sdl-audio.h>
#include <sdl-timer.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

SdlAudioCallback_t sdl_audio_callback;

int SDL_OpenAudio(SDL_AudioSpec *desired, SDL_AudioSpec *obtained) {
  if (desired == NULL) {
    return -1;
  }
  NDL_OpenAudio(desired->freq, desired->channels, desired->samples);
  if (obtained != NULL) {
    memcpy(obtained, desired, sizeof(SDL_AudioSpec));
  }
  sdl_audio_callback = (SdlAudioCallback_t){
      .callback = desired->callback,
      .userdata = desired->userdata,
      .last_called = 0,
      .interval = desired->freq * 1000 / desired->samples,
      .buf_size = desired->samples * desired->channels,
      .valid = true,
      .paused = false,
      .locked = false,
  };
  return 0;
}

void SDL_CloseAudio(void) {
  NDL_CloseAudio();
  sdl_audio_callback.valid = false;
}

void SDL_PauseAudio(int pause_on) {
  sdl_audio_callback.paused = pause_on != 0;
}

void SDL_MixAudio(uint8_t *dst, uint8_t *src, uint32_t len, int volume) {}

SDL_AudioSpec *
SDL_LoadWAV(const char *file, SDL_AudioSpec *spec, uint8_t **audio_buf, uint32_t *audio_len) {
  return NULL;
}

void SDL_FreeWAV(uint8_t *audio_buf) {}

void SDL_LockAudio(void) {
  sdl_audio_callback.locked = true;
}

void SDL_UnlockAudio(void) {
  sdl_audio_callback.locked = false;
}

void sdl_schedule_audio_callback(void) {
  static bool reent = false;
  if (reent || !sdl_audio_callback.valid || sdl_audio_callback.paused
      || sdl_audio_callback.locked) {
    return;
  }
  reent = true;
  if (NDL_GetTicks() - sdl_audio_callback.last_called < sdl_audio_callback.interval) {
    const int size = sdl_audio_callback.buf_size;
    uint8_t *const buf = (uint8_t *)calloc(size, sizeof(uint8_t));
    sdl_audio_callback.callback(sdl_audio_callback.userdata, buf, size);
    NDL_PlayAudio(buf, size);
    sdl_audio_callback.last_called = NDL_GetTicks();
  }
  reent = false;
}
