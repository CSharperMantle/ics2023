#include "uart16550.h"
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

void putch(char ch) {
  Uart16550Lsr_t lsr;
  do {
    lsr.as_u8 = inb(PERIP_UART16550_ADDR + UART16550_REG_LSR);
  } while (!lsr.thre);
  outb(PERIP_UART16550_ADDR + UART16550_REG_TXR, ch);
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

static inline void bootstrap_sram(void) {
  const size_t len = &_edata - &_data;
  uint8_t *const pdata_b = (uint8_t *)&_data;
  const uint8_t *const pdata_load_b = (uint8_t *)&_data_load;
  for (size_t i = 0; i < len; i++) {
    pdata_b[i] = pdata_load_b[i];
  }
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

void _trm_init(void) {
  // Initialized SRAM content
  bootstrap_sram();
  init_uart16550();

  const int ret = main(mainargs);
  halt(ret);
}
