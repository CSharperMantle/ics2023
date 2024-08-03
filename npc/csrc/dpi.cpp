#include <cassert>
#include <cstdio>

#include "common.hpp"
#include "debug.hpp"
#include "dpi.hpp"
#include "mem/host.hpp"
#include "mem/paddr.hpp"

DutDpiState dut_dpi_state;

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

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
  const paddr_t addr_ = static_cast<paddr_t>(addr) & ~0x3u;
  assert(in_flash(addr_));
  *data = do_flash_read(flash_guest_to_host(addr_));
}

void mrom_read(int32_t addr, int32_t *data) {
  const paddr_t addr_ = static_cast<paddr_t>(addr) & ~0x3u;
  assert(in_mrom(addr_));
  *data = do_mrom_read(mrom_guest_to_host(addr_));
}

#ifdef __cplusplus
}
#endif /* __cplusplus */
