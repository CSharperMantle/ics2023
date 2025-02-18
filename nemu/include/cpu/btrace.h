#ifndef BTRACE_INCLUDED_H_
#define BTRACE_INCLUDED_H_

#include <isa.h>
#include <stdint.h>
#include <stdio.h>

typedef struct BTraceEntry_ {
  union {
    word_t as_word_t;
    uint8_t as_bytes[sizeof(word_t)];
  } pc;
  uint8_t is_branch;
  uint8_t taken;
} BTraceEntry_t;

extern FILE *file_btrace;

void init_btrace(const char *path);
void write_btrace(word_t pc, bool taken);
void flush_btrace(void);
void close_btrace(void);

#endif
