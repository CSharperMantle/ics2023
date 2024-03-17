#include <NDL.h>
#include <assert.h>
#include <sdl-event.h>
#include <stdio.h>
#include <string.h>

#define keyname(k) #k,

static const char *KEYNAME[] = {"NONE", _KEYS(keyname)};

static uint8_t key_state[SDLK_LEN_] = {0};

int SDL_PushEvent(SDL_Event *ev) {
  return 0;
}

int SDL_PollEvent(SDL_Event *ev) {
  static char buf[32];

  int len = NDL_PollEvent(buf, sizeof(buf));
  if (len <= 0) {
    return 0;
  }

  if (ev == NULL) {
    // TODO: When ev is NULL, cache current event.
    return 1;
  }

  char device, type;
  static char key_name[16];

  int n_matched = sscanf(buf, "%c%c %15s", &device, &type, key_name);
  assert(device == 'k');
  assert(n_matched == 3);

  ev->type = type == 'd' ? SDL_KEYDOWN : SDL_KEYUP;
  ev->key.keysym.sym = 0;
  for (size_t i = 0; i < sizeof(KEYNAME) / sizeof(KEYNAME[0]); i++) {
    if (!strcmp(KEYNAME[i], key_name)) {
      ev->key.keysym.sym = i;
      key_state[i] = type == 'd';
      break;
    }
  }

  return 1;
}

int SDL_WaitEvent(SDL_Event *ev) {
  int ret = 0;
  do {
    ret = SDL_PollEvent(ev);
  } while (ret == 0);
  return ret;
}

int SDL_PeepEvents(SDL_Event *ev, int numevents, int action, uint32_t mask) {
  assert(0);
  return 0;
}

uint8_t *SDL_GetKeyState(int *numkeys) {
  if (numkeys != NULL) {
    *numkeys = SDLK_LEN_;
  }
  return key_state;
}
