#include <cassert>
#include <cstdio>

#include "common.hpp"
#include "debug.hpp"
#include "dpi.hpp"
#include "mem/host.hpp"
#include "mem/paddr.hpp"

/*
import "DPI-C" function void soc_dpi_ebreak(input bad);
import "DPI-C" function void soc_dpi_report_pc(input $xLenType pc);
import "DPI-C" function void soc_dpi_report_cycles(input byte pc);
*/

DutDpiState dut_dpi_state;

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

word_t npc_dpi_pmem_read(sword_t mem_r_addr) {
  assert(0);
  return 0;
}
void npc_dpi_pmem_write(char mem_mask, word_t mem_w_addr, word_t mem_w_data) {
  assert(0);
}

void soc_dpi_ebreak(bool bad) {
  dut_dpi_state.ebreak = true;
  dut_dpi_state.bad = bad;
}

void soc_dpi_report_state(bool retired, word_t pc, uint8_t cycles, uint32_t instr, word_t a0) {
  dut_dpi_state.retired = retired;
  dut_dpi_state.pc = pc;
  dut_dpi_state.instr_cycles = cycles;
  dut_dpi_state.instr = instr;
  dut_dpi_state.reg_a0 = a0;
}

void flash_read(int32_t addr, int32_t *data) {
  assert(0);
}

void mrom_read(int32_t addr, int32_t *data) {
  assert(0);
}

#ifdef __cplusplus
}
#endif /* __cplusplus */
