#include <am.h>
#include <klib.h>
#include <klib-macros.h>
#include <stdarg.h>

#if !defined(__ISA_NATIVE__) || defined(__NATIVE_USE_KLIB__)

int printf(const char *fmt, ...) {
  panic("Not implemented");
}

int vsprintf(char *out, const char *fmt, va_list ap) {
  int len = 0;
  const char *fmt_cur = fmt;
  char *out_cur = out;
  while (*fmt_cur != '\0') {
    if (!strncmp(fmt_cur, "%s", 2)) {
      char *str = va_arg(ap, char *);
      int l = strlen(str);
      strcat(out_cur, str);
      out_cur += l;
      fmt_cur += 2;
      len += l;
    } else if (!strncmp(fmt_cur, "%d", 2)) {
      int val = va_arg(ap, int);
      int l;
      itoa(val, out_cur, 10, &l);
      out_cur += l;
      fmt_cur += 2;
      len += l;
    } else {
      *(out_cur++) = *(fmt_cur++);
      len++;
    }
  }
  *out_cur = '\0';
  len++;
  return len;
}

int sprintf(char *out, const char *fmt, ...) {
  va_list args;
  va_start(args, fmt);
  int result = vsprintf(out, fmt, args);
  va_end(args);
  return result;
}

int snprintf(char *out, size_t n, const char *fmt, ...) {
  panic("Not implemented");
}

int vsnprintf(char *out, size_t n, const char *fmt, va_list ap) {
  panic("Not implemented");
}

#endif
