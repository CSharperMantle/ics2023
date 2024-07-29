#include <am.h>
#include <klib.h>

int isspace(int c) {
  return (c == ' ' || (c >= '\t' && c <= '\r'));
}

int isxdigit(int c) {
  return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
}

int isdigit(int c) {
  return (c >= '0' && c <= '9');
}

int isalpha(int c) {
  return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
}

int toupper(int c) {
  if (c >= 'a' && c <= 'z') {
    return c - 'a' + 'A';
  }
  return c;
}