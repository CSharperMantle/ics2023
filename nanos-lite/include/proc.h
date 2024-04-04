#ifndef __PROC_H__
#define __PROC_H__

#include <common.h>
#include <memory.h>

#define MAX_NR_PROC 4
#define STACK_SIZE (8 * PGSIZE)

#define SCHED_FG_SLICES 30

typedef union {
  struct {
    uint8_t stack[STACK_SIZE] PG_ALIGN;
  };
  struct {
    Context *cp;
    AddrSpace as;
    // we do not free memory, so use `max_brk' to determine when to call _map()
    uintptr_t max_brk;
  };
} PCB;

extern PCB *current;

void init_proc(void);
void switch_boot_pcb(void);
Context *schedule(Context *ctx);

#endif
