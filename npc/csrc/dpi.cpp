#include "VTop.h"
#include "common.hpp"
#include "debug.hpp"
#include "difftest.hpp"
#include "pmem.hpp"

extern VTop dut;

extern "C" void npc_dpi_ifu(int pc, int *instr) {
  const word_t upc = static_cast<word_t>(pc);
  if (upc > PMEM_LEFT && upc <= PMEM_RIGHT - 4) {
    *instr = *reinterpret_cast<uint32_t *>(guest_to_host(upc));
  } else {
    *instr = 0;
  }
}

extern "C" word_t npc_dpi_pmem_read(word_t mem_r_addr) {
  return *reinterpret_cast<word_t *>(guest_to_host(mem_r_addr));
}

extern "C" void npc_dpi_pmem_write(char mem_width, word_t mem_w_addr, word_t mem_w_data) {
  switch (mem_width) {
    case 0: *reinterpret_cast<uint8_t *>(guest_to_host(mem_w_addr)) = mem_w_data & 0xFF; break;
    case 1: *reinterpret_cast<uint16_t *>(guest_to_host(mem_w_addr)) = mem_w_data & 0xFFFF; break;
    case 2: *reinterpret_cast<uint32_t *>(guest_to_host(mem_w_addr)) = mem_w_data; break;
    default: panic("bad mem_width %hhd", mem_width);
  }
}