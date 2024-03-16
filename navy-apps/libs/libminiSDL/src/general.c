#include <NDL.h>
#include <sdl-general.h>

int SDL_Init(uint32_t flags) {
  int ret = NDL_Init(flags);
  if (ret) {
    return ret;
  }

  extern uint32_t sdl_init_ticks;
  sdl_init_ticks = NDL_GetTicks();

  return 0;
}

void SDL_Quit(void) {
  NDL_Quit();
}

char *SDL_GetError(void) {
  return "Navy does not support SDL_GetError()";
}

int SDL_SetError(const char *fmt, ...) {
  return -1;
}

int SDL_ShowCursor(int toggle) {
  return 0;
}

void SDL_WM_SetCaption(const char *title, const char *icon) {}
