#ifndef DPI_HPP_INCLUDED_
#define DPI_HPP_INCLUDED_

#include <cstdint>

#include "common.hpp"

struct DutDpiState {
  bool ebreak;
  bool bad;
  bool retired;
  uint8_t instr_cycles;
  word_t pc;
  uint32_t instr;
  word_t reg_a0;
};

extern DutDpiState dut_dpi_state;

#endif /* DPI_HPP_INCLUDED_ */