#include <am.h>

#ifndef __ISA_RISCV32E__
// For clangd
#include "../../platform/ysyxsoc/include/ysyxsoc.h"
#else
#include <ysyxsoc.h>
#endif

extern char _heap_start;
int main(const char *args);

extern char _sram_start;
#define SRAM_SIZE (8 * 1024)
#define SRAM_END  ((uintptr_t)&_sram_start + SRAM_SIZE)

Area heap = RANGE(&_heap_start, SRAM_END);
#ifndef MAINARGS
#define MAINARGS ""
#endif
static const char mainargs[] = MAINARGS;

#define PERIP_UART16550_OFF_TXR 0x0l

void putch(char ch) {
  outb(PERIP_UART16550_ADDR + PERIP_UART16550_OFF_TXR, ch);
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
