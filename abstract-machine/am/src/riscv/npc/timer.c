#include <am.h>

#ifndef __ISA_RISCV32E__
// For clangd
#include "../../platform/npc/include/npc.h"
#else
#include <npc.h>
#endif

#define CLINT_MTIME_REG_L (CLINT_ADDR + 0xbff8)
#define CLINT_MTIME_REG_H (CLINT_MTIME_REG_L + 0x4)

void __am_timer_init() {}

void __am_timer_uptime(AM_TIMER_UPTIME_T *uptime) {
  const uint32_t cycles_l = inl(CLINT_MTIME_REG_L);
  const uint32_t cycles_h = inl(CLINT_MTIME_REG_H);
  uptime->us = (((uint64_t)cycles_h << 32) | ((uint64_t)cycles_l)) / 1000 * NS_PER_CYCLE;
}

void __am_timer_rtc(AM_TIMER_RTC_T *rtc) {
  rtc->second = 0;
  rtc->minute = 0;
  rtc->hour = 0;
  rtc->day = 0;
  rtc->month = 0;
  rtc->year = 1900;
}
