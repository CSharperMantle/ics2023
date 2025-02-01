#ifndef DPI_HPP_INCLUDED_
#define DPI_HPP_INCLUDED_

#include <cstdint>

#include "common.hpp"

struct InstrPerfCounters {
  uint16_t total_cycles;
  uint16_t ifu_cycles;
  uint16_t idu_cycles;
  uint16_t exu_cycles;
  uint16_t lsu_cycles;
  uint16_t wbu_cycles;
};

struct GlobalPerfCounters {
  uint32_t icache_hit;
  uint32_t icache_miss;
  uint32_t pred_hit;
  uint32_t pred_miss;
};

struct DutDpiState {
  bool ebreak;
  bool bad;
  bool retired;
  bool mem_en;
  InstrPerfCounters i_ctrs;
  GlobalPerfCounters g_ctrs;
  word_t pc;
  uint32_t instr;
  word_t rw_addr;
};

extern DutDpiState dut_dpi_state;

#endif /* DPI_HPP_INCLUDED_ */