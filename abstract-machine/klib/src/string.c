#include <klib-macros.h>
#include <klib.h>
#include <stddef.h>
#include <stdint.h>

#if !defined(__ISA_NATIVE__) || defined(__NATIVE_USE_KLIB__)

size_t strlen(const char *s) {
  size_t len = 0;
  while (*(s++) != '\0') {
    len++;
  }
  return len;
}

char *strcpy(char *dst, const char *src) {
  char *orig = dst;
  while (*src != '\0') {
    *(dst++) = *(src++);
  }
  *dst = '\0';
  return orig;
}

char *strncpy(char *dst, const char *src, size_t n) {
  char *orig = dst;
  while (*src != '\0' && n--) {
    *(dst++) = *(src++);
  }
  while (n--) {
    *(dst++) = '\0';
  }
  return orig;
}

char *strcat(char *dst, const char *src) {
  char *orig = dst;
  while (*(dst++) != '\0') {
    ;
  }
  dst--;
  while (*src != '\0') {
    *(dst++) = *(src++);
  }
  *dst = '\0';
  return orig;
}

int strcmp(const char *s1, const char *s2) {
  while (*s1 != '\0' || *s2 != '\0') {
    if (*s1 != *s2) {
      return (*s1 > *s2) - (*s1 < *s2);
    }
    s1++;
    s2++;
  }
  return 0;
}

int strncmp(const char *s1, const char *s2, size_t n) {
  while (n-- && (*s1 != '\0' || *s2 != '\0')) {
    if (*s1 != *s2) {
      return (*s1 > *s2) - (*s1 < *s2);
    }
    s1++;
    s2++;
  }
  return 0;
}

void *memset(void *s, int c, size_t n) {
  uint8_t *const buf_b = (uint8_t *)s;
  for (size_t i = 0; i < n; i++) {
    buf_b[i] = (uint8_t)c;
  }
  return s;
}

void *memmove(void *dst, const void *src, size_t n) {
  if (n == 0) {
    return dst;
  }
  if (dst + n <= src || src + n <= dst) {
    return memcpy(dst, src, n);
  }
  unsigned char *buf_dst = (unsigned char *)dst;
  unsigned char *buf_src = (unsigned char *)src;
  if (dst < src) {
    for (size_t i = 0; i < n; i++) {
      buf_dst[i] = buf_src[i];
    }
  } else if (dst > src) {
    while (n--) {
      buf_dst[n] = buf_src[n];
    }
  }
  return dst;
}

void *memcpy(void *restrict out, const void *restrict in, size_t n) {
  uint8_t *const out_b = (uint8_t *)out;
  uint8_t *const in_b = (uint8_t *)in;
  for (size_t i = 0; i < n; i++) {
    out_b[i] = in_b[i];
  }
  return out;
}

int memcmp(const void *s1, const void *s2, size_t n) {
  if (n == 0) {
    return 0;
  }
  const unsigned char *ps1 = (const unsigned char *)s1;
  const unsigned char *ps2 = (const unsigned char *)s2;
  while (n--) {
    if (*ps1 != *ps2) {
      return (*ps1 > *ps2) - (*ps1 < *ps2);
    }
    ps1++;
    ps2++;
  }
  return 0;
}

#endif
