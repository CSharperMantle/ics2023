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
  while (1)
    ;
  __builtin_unreachable();
}

extern char _data;
extern char _edata;
extern char _data_load;
extern char _bss_start;
extern char _ebss;

static inline void bootstrap_sram(void) {
  const size_t data_len = &_edata - &_data;
  uint8_t *const pdata_b = (uint8_t *)&_data;
  const uint8_t *const pdata_load_b = (uint8_t *)&_data_load;
  memcpy(pdata_b, pdata_load_b, data_len);

  const size_t bss_len = &_ebss - &_bss_start;
  uint8_t *const bss_start_b = (uint8_t *)&_bss_start;
  memset(bss_start_b, 0, bss_len);
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
  // Initialized SRAM content
  bootstrap_sram();
  init_uart16550();
  print_vendor_info();

  const int ret = main(mainargs);
  halt(ret);
}
