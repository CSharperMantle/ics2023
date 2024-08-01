#include <am.h>

#ifndef __ISA_RISCV32E__
// For clangd
#include "../../platform/npc/include/npc.h"
#else
#include <npc.h>
#endif

#define RTC_ADDR_REG_US_L (RTC_ADDR + 0)
#define RTC_ADDR_REG_US_H (RTC_ADDR + 4)

void __am_timer_init() {}

void __am_timer_uptime(AM_TIMER_UPTIME_T *uptime) {
  const uint32_t us_l = inl(RTC_ADDR_REG_US_L);
  const uint32_t us_h = inl(RTC_ADDR_REG_US_H);
  uptime->us = ((uint64_t)us_h << 32) | ((uint64_t)us_l);
}

void __am_timer_rtc(AM_TIMER_RTC_T *rtc) {
  rtc->second = 0;
  rtc->minute = 0;
  rtc->hour = 0;
  rtc->day = 0;
  rtc->month = 0;
  rtc->year = 1900;
}
