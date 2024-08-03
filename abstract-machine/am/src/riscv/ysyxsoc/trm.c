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
extern char _sram_end;
Area heap = RANGE(&_heap_start, &_sram_end);

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

extern char _data;
extern char _edata;
extern char _data_load;

void _trm_init(void) {
  // Initialized SRAM content
  const size_t len = &_edata - &_data;
  uint8_t *const pdata_b = (uint8_t *)&_data;
  const uint8_t *const pdata_load_b = (uint8_t *)&_data_load;
  for (size_t i = 0; i < len; i++) {
    pdata_b[i] = pdata_load_b[i];
  }

  const int ret = main(mainargs);
  halt(ret);
}
