#ifndef __ISA_RISCV32E__
// For clangd
#include "../../platform/ysyxsoc/include/ysyxsoc.h"
#else
#include <ysyxsoc.h>
#endif
#include <am.h>
#include <klib-macros.h>

static void am_uart_init(void) {
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
  outb(PERIP_UART16550_ADDR + UART16550_REG_DLL, 0x01); // TODO: Make it concrete
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

static void am_uart_config(AM_UART_CONFIG_T *cfg) {
  cfg->present = true;
}

static void am_uart_tx(AM_UART_TX_T *tx) {
  Uart16550Lsr_t lsr;
  do {
    lsr.as_u8 = inb(PERIP_UART16550_ADDR + UART16550_REG_LSR);
  } while (!lsr.thre);
  outb(PERIP_UART16550_ADDR + UART16550_REG_TXR, tx->data);
}

static void am_uart_rx(AM_UART_RX_T *rx) {
  Uart16550Lsr_t lsr;
  lsr.as_u8 = inb(PERIP_UART16550_ADDR + UART16550_REG_LSR);
  rx->data = lsr.dr ? inb(PERIP_UART16550_ADDR + UART16550_REG_RXR) : 0xff;
}

static void am_timer_init(void) {}

static void am_timer_config(AM_TIMER_CONFIG_T *cfg) {
  cfg->present = true;
  cfg->has_rtc = false;
}

static void am_timer_uptime(AM_TIMER_UPTIME_T *uptime) {
  const uint32_t cycles_l = inl(PERIP_CLINT_ADDR + CLINT_REG_MTIME_L);
  const uint32_t cycles_h = inl(PERIP_CLINT_ADDR + CLINT_REG_MTIME_H);
  uptime->us = (((uint64_t)cycles_h << 32) | ((uint64_t)cycles_l)) / 1000 * NS_PER_CYCLE;
}

static void am_input_config(AM_INPUT_CONFIG_T *cfg) {
  cfg->present = false;
}

static void am_gpu_config(AM_GPU_CONFIG_T *cfg) {
  cfg->present = false;
}

typedef void (*handler_t)(void *);
static void *regs[128] = {
    [AM_UART_CONFIG] = am_uart_config,
    [AM_UART_TX] = am_uart_tx,
    [AM_UART_RX] = am_uart_rx,
    [AM_TIMER_CONFIG] = am_timer_config,
    [AM_TIMER_UPTIME] = am_timer_uptime,
    [AM_INPUT_CONFIG] = am_input_config,
    [AM_GPU_CONFIG] = am_gpu_config,
};

static void no_reg(void *buf) {
  panic("access nonexist register");
}

bool ioe_init(void) {
  static bool inited = false;

  if (inited) {
    return true;
  }

  am_uart_init();
  am_timer_init();

  for (int i = 0; i < LENGTH(regs); i++) {
    if (regs[i] == NULL) {
      regs[i] = no_reg;
    }
  }

  inited = true;
  return true;
}

void ioe_read(int reg, void *buf) {
  ((handler_t)regs[reg])(buf);
}

void ioe_write(int reg, void *buf) {
  ((handler_t)regs[reg])(buf);
}
