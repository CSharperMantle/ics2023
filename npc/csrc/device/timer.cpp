#include <chrono>
#include <cstdint>
#include <cstdio>
#include <ctime>

#include "common.hpp"
#include "debug.hpp"
#include "device/map.hpp"

static uint64_t boot_time = 0;

static uint64_t get_time_internal() {
  const auto now = std::chrono::steady_clock::now().time_since_epoch();
  const auto ns = std::chrono::duration_cast<std::chrono::microseconds>(now);
  return ns.count();
}

static uint64_t get_time() {
  if (boot_time == 0)
    boot_time = get_time_internal();
  uint64_t now = get_time_internal();
  return now - boot_time;
}

static uint32_t *rtc_port_base = NULL;

static void rtc_io_handler(uint32_t offset, uint8_t mask, bool is_write) {
  assert(offset == 0 || offset == 4);
  if (!is_write) {
    const uint64_t us = get_time();
    rtc_port_base[0] = (uint32_t)us;
    rtc_port_base[1] = us >> 32;
  }
}

void init_timer() {
  rtc_port_base = (uint32_t *)new_space(8);
  add_mmio_map("rtc", CONFIG_RTC_MMIO, rtc_port_base, 8, rtc_io_handler);
}