#include <cassert>
#include <cstdio>

#include "common.hpp"
#include "debug.hpp"
#include "dpi.hpp"
#include "mem/host.hpp"
#include "mem/paddr.hpp"

DutDpiState dut_dpi_state;

#ifdef CONFIG_MTRACE
static void print_mtrace(const char *tag, paddr_t addr, bool read, word_t data, uint8_t mask) {
  const word_t pc = static_cast<word_t>(dut_dpi_state.pc);
  if (read) {
    if (mask == 0) {
      Log("pc=" FMT_WORD ": %s: %s " FMT_PADDR "; ->", pc, tag, "R", addr);
    } else {
      Log("pc=" FMT_WORD ": %s: %s " FMT_PADDR "; <- data=" FMT_WORD, pc, tag, "R", addr, data);
    }
  } else {
    Log("pc=" FMT_WORD ": %s: %s " FMT_PADDR "; data=" FMT_WORD "; mask=0x%02hhx",
        pc,
        tag,
        "W",
        addr,
        data,
        mask);
  }
}
#endif

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

void soc_dpi_ebreak(void) {
  dut_dpi_state.ebreak = true;
}

void soc_dpi_report_state(bool retired, word_t pc, uint16_t cycles, uint32_t instr, word_t a0, bool bad) {
  dut_dpi_state.retired = retired;
  dut_dpi_state.pc = pc;
  dut_dpi_state.instr_cycles = cycles;
  dut_dpi_state.instr = instr;
  dut_dpi_state.reg_a0 = a0;
  dut_dpi_state.bad = bad;
}

void soc_dpi_psram_read(int32_t addr, int32_t *data) {
  const paddr_t addr_ = static_cast<paddr_t>(addr + PSRAM_LEFT) & ~0x3u;
  assert(in_psram(addr_));
  *data = do_psram_read(psram_guest_to_host(addr_));
#ifdef CONFIG_MTRACE
  print_mtrace("psram", addr, true, *data, 0x0f);
#endif
}

void soc_dpi_psram_write(uint32_t addr, uint32_t data) {
  const paddr_t addr_ = static_cast<paddr_t>(addr + PSRAM_LEFT) & ~0x3u;
  assert(in_psram(addr_));
  do_psram_write(psram_guest_to_host(addr_), data);
#ifdef CONFIG_MTRACE
  print_mtrace("psram", addr, false, data, 0x0f);
#endif
}

void mrom_read(int32_t addr, int32_t *data) {
  const paddr_t addr_ = static_cast<paddr_t>(addr) & ~0x3u;
  assert(in_mrom(addr_));
  *data = do_mrom_read(mrom_guest_to_host(addr_));
#ifdef CONFIG_MTRACE
  print_mtrace("mrom", addr, true, *data, 0x0f);
#endif
}

void flash_read(int32_t addr, int32_t *data) {
  const paddr_t addr_ = static_cast<paddr_t>(addr + FLASH_LEFT) & ~0x3u;
  assert(in_flash(addr_));
  *data = do_flash_read(flash_guest_to_host(addr_));
#ifdef CONFIG_MTRACE
  print_mtrace("flash", addr, true, *data, 0x0f);
#endif
}

#ifdef __cplusplus
}
#endif /* __cplusplus */
