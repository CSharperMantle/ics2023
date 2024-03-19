#include <stdio.h>
#include <stdlib.h>

#define SDL_malloc  malloc
#define SDL_free    free
#define SDL_realloc realloc

#define SDL_STBIMAGE_IMPLEMENTATION
#include "SDL_stbimage.h"

SDL_Surface *IMG_Load_RW(SDL_RWops *src, int freesrc) {
  assert(src->type == RW_TYPE_MEM);
  assert(freesrc == 0);
  return NULL;
}

SDL_Surface *IMG_Load(const char *filename) {
  int ret;
  FILE *const f = fopen(filename, "rb");
  if (f == NULL) {
    return NULL;
  }
  fseek(f, 0, SEEK_END);
  const long size = ftell(f);
  uint8_t *const buf = (uint8_t *)malloc(sizeof(uint8_t) * size);
  rewind(f);
  const size_t actual_size = fread(buf, sizeof(uint8_t), size, f);
  assert(actual_size <= size);
  fclose(f);
  SDL_Surface *const surface = STBIMG_LoadFromMemory(buf, (int)actual_size);
  free(buf);
  return surface;
}

int IMG_isPNG(SDL_RWops *src) {
  return 0;
}

SDL_Surface *IMG_LoadJPG_RW(SDL_RWops *src) {
  return IMG_Load_RW(src, 0);
}

char *IMG_GetError() {
  return "Navy does not support IMG_GetError()";
}
