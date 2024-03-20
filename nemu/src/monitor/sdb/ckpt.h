#ifndef CKPT_H_INCLUDED_
#define CKPT_H_INCLUDED_

#include <isa.h>
#include <stdint.h>
#include <stddef.h>

typedef struct HwState_ {
  CPU_state cpu;
} HwState_t;

int ckpt_load_from(const char *filename);
int ckpt_save_to(const char *filename);

#endif
