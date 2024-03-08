#include <am.h>
#include <nemu.h>

static volatile uint32_t *const RTC_PORT_BASE = (volatile uint32_t *)0xa0000048;

void __am_timer_init() {
}

void __am_timer_uptime(AM_TIMER_UPTIME_T *uptime) {
  uptime->us = ((uint64_t)RTC_PORT_BASE[1] << 32) | ((uint64_t)RTC_PORT_BASE[0]);
}

void __am_timer_rtc(AM_TIMER_RTC_T *rtc) {
  rtc->second = 0;
  rtc->minute = 0;
  rtc->hour   = 0;
  rtc->day    = 0;
  rtc->month  = 0;
  rtc->year   = 1900;
}
