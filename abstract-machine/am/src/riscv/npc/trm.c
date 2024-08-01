#include <am.h>

#ifndef __ISA_RISCV32E__
// For clangd
#include "../../platform/npc/include/npc.h"
#else
#include <npc.h>
#endif

extern char _heap_start;
int main(const char *args);

extern char _pmem_start;
#define PMEM_SIZE (128 * 1024 * 1024)
#define PMEM_END  ((uintptr_t)&_pmem_start + PMEM_SIZE)

Area heap = RANGE(&_heap_start, PMEM_END);
#ifndef MAINARGS
#define MAINARGS ""
#endif
static const char mainargs[] = MAINARGS;

void putch(char ch) {
  outb(UART_ADDR, ch);
}

__attribute__((noreturn)) void halt(int code) {
  nemu_trap(code);
  __builtin_unreachable();
  while (1)
    ;
}

void _trm_init() {
  int ret = main(mainargs);
  halt(ret);
}
