#ifndef __ISA_RISCV32E__
// For clangd
#include "../../platform/ysyxsoc/include/ysyxsoc.h"
#else
#include <ysyxsoc.h>
#endif
#include <am.h>
#include <klib.h>

extern char _heap_start;
int main(const char *args);

extern char _psram_end;
Area heap = RANGE(&_heap_start, &_psram_end);

#ifndef MAINARGS
#error no mainargs
#define MAINARGS ""
#endif
static const char mainargs[] = MAINARGS;

void putch(char ch) {
  io_write(AM_UART_TX, ch);
}

__attribute__((noreturn)) void halt(int code) {
  nemu_trap(code);
  while (1)
    ;
  __builtin_unreachable();
}

static void display_vendor_info(void) {
  uintptr_t marchid;
  asm volatile("csrr %0, marchid" : "=r"(marchid));
  for (size_t i = 0; i < 4; i++) {
    uint8_t b = 0;
    b |= (marchid % 10) & 0xf;
    marchid /= 10;
    b |= ((marchid % 10) & 0xf) << 4;
    marchid /= 10;
    outb(PERIP_GPIO_CTRL_ADDR + GPIO_CTRL_REG_SEGS + i, b);
  }
}

void _trm_init(void) {
  ioe_init();
  display_vendor_info();

  const int ret = main(mainargs);
  halt(ret);
}
