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
#define MAINARGS ""
#endif
static const char mainargs[] = MAINARGS;

void putch(char ch) {
  Uart16550Lsr_t lsr;
  do {
    lsr.as_u8 = inb(PERIP_UART16550_ADDR + UART16550_REG_LSR);
  } while (!lsr.thre);
  outb(PERIP_UART16550_ADDR + UART16550_REG_TXR, ch);
}

__attribute__((noreturn)) void halt(int code) {
  nemu_trap(code);
  while (1)
    ;
  __builtin_unreachable();
}

static inline void init_uart16550(void) {
  Uart16550Lcr_t lcr;
  lcr = (Uart16550Lcr_t){
      .dlab = 1,
      .set_break = 0,
      .stick_parity = 0,
      .eps = 0,
      .pen = 0,
      .stb = 0,
      .wls = 0b11,
  };
  outb(PERIP_UART16550_ADDR + UART16550_REG_LCR, lcr.as_u8);
  outb(PERIP_UART16550_ADDR + UART16550_REG_DLM, 0x00);
  outb(PERIP_UART16550_ADDR + UART16550_REG_DLL, 0x02); // TODO: Make it concrete
  lcr = (Uart16550Lcr_t){
      .dlab = 0,
      .set_break = 0,
      .stick_parity = 0,
      .eps = 0,
      .pen = 0,
      .stb = 0,
      .wls = 0b11,
  };
  outb(PERIP_UART16550_ADDR + UART16550_REG_LCR, lcr.as_u8);
}

static void print_vendor_info(void) {
  uintptr_t mvendorid, marchid;
  asm volatile("csrr %0, mvendorid" : "=r"(mvendorid));
  asm volatile("csrr %0, marchid" : "=r"(marchid));
  printf("AM on NPC mvendorid=0x%08x; marchid=0x%08x\n", mvendorid, marchid);
}

void _trm_init(void) {
  init_uart16550();
  print_vendor_info();

  const int ret = main(mainargs);
  halt(ret);
}
