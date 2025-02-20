#ifndef BTRACE_INCLUDED_H_
#define BTRACE_INCLUDED_H_

#include <isa.h>
#include <stdint.h>
#include <stdio.h>

typedef enum BTraceEntryType_ {
  BTRACE_NOT_BRANCH = 0,
  BTRACE_CONDITIONAL,
  BTRACE_JUMP,
  BTRACE_CALL,
  BTRACE_RETURN,
} BTraceEntryType_t;

typedef struct BTraceEntry_ {
  union {
    word_t as_word_t;
    uint8_t as_bytes[sizeof(word_t)];
  } pc, target;
  uint8_t type;
  uint8_t taken;
} BTraceEntry_t;

extern FILE *file_btrace;

void init_btrace(const char *path);
void write_btrace(word_t pc, BTraceEntryType_t type, bool taken, word_t target);
void flush_btrace(void);
void close_btrace(void);

#endif
