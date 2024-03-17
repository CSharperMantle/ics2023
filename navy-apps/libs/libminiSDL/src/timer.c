#include <NDL.h>
#include <sdl-timer.h>
#include <stdio.h>

uint32_t sdl_init_ticks = 0;

SDL_TimerID SDL_AddTimer(uint32_t interval, SDL_NewTimerCallback callback, void *param) {
  return NULL;
}

int SDL_RemoveTimer(SDL_TimerID id) {
  return 1;
}

uint32_t SDL_GetTicks(void) {
  extern void sdl_schedule_audio_callback(void);
  sdl_schedule_audio_callback();
  
  return NDL_GetTicks() - sdl_init_ticks;
}

void SDL_Delay(uint32_t ms) {
  const uint32_t start = SDL_GetTicks();
  uint32_t now;
  do {
    now = SDL_GetTicks();
  } while (now - start < ms);
}
