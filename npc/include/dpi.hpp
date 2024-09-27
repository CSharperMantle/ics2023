#ifndef DPI_HPP_INCLUDED_
#define DPI_HPP_INCLUDED_

#include <cstdint>

#include "common.hpp"

struct DutDpiState {
  bool ebreak;
  bool bad;
  bool retired;
  bool mem_en;
  uint16_t instr_cycles;
  word_t pc;
  uint32_t instr;
  word_t reg_a0;
  word_t rw_addr;
};

extern DutDpiState dut_dpi_state;

#endif /* DPI_HPP_INCLUDED_ */