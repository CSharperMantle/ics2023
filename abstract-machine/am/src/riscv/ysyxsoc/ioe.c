#include <am.h>
#include <klib-macros.h>
#ifndef __ISA_RISCV32E__
// For clangd
#include "../../platform/ysyxsoc/include/ysyxsoc.h"
#else
#include <ysyxsoc.h>
#endif

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
    [AM_TIMER_CONFIG] = am_timer_config,
    [AM_TIMER_UPTIME] = am_timer_uptime,
    [AM_INPUT_CONFIG] = am_input_config,
    [AM_GPU_CONFIG] = am_gpu_config,
};

static void no_reg(void *buf) {
  panic("access nonexist register");
}

bool ioe_init(void) {
  for (int i = 0; i < LENGTH(regs); i++) {
    if (regs[i] == NULL) {
      regs[i] = no_reg;
    }
  }

  am_timer_init();
  return true;
}

void ioe_read(int reg, void *buf) {
  ((handler_t)regs[reg])(buf);
}

void ioe_write(int reg, void *buf) {
  ((handler_t)regs[reg])(buf);
}
