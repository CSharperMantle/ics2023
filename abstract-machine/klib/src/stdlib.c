#include <am.h>
#include <klib-macros.h>
#include <klib.h>

#if !defined(__ISA_NATIVE__) || defined(__NATIVE_USE_KLIB__)
static unsigned long int next = 1;

int rand(void) {
  // RAND_MAX assumed to be 32767
  next = next * 1103515245 + 12345;
  return (unsigned int)(next / 65536) % 32768;
}

void srand(unsigned int seed) {
  next = seed;
}

int abs(int x) {
  return (x < 0 ? -x : x);
}

int atoi(const char *nptr) {
  int x = 0;
  while (*nptr == ' ') {
    nptr++;
  }
  while (*nptr >= '0' && *nptr <= '9') {
    x = x * 10 + *nptr - '0';
    nptr++;
  }
  return x;
}

static void *malloc_last_addr = NULL;

void *malloc(size_t size) {
  if (malloc_last_addr == NULL) {
    malloc_last_addr = heap.start;
  }
  size_t size_adj = size & 0xF ? (size & ~(size_t)0xF) + 0x10 : size;
  void *ptr = malloc_last_addr;
  malloc_last_addr = (uint8_t *)malloc_last_addr + size_adj;
  return ptr;
}

void free(void *ptr) {}

#endif
