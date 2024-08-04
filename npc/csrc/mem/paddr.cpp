#include <array>
#include <cstdint>

#include "common.hpp"
#include "debug.hpp"
#include "dpi.hpp"
#include "mem/paddr.hpp"

#ifdef CONFIG_MTRACE
static void print_mtrace(paddr_t addr, bool read, word_t data, uint8_t mask) {
  const word_t pc = static_cast<word_t>(dut_dpi_state.pc);
  if (read) {
    if (mask == 0) {
      Log("pc=" FMT_WORD ": mem: %s " FMT_PADDR "; ->", pc, "R", addr);
    } else {
      Log("pc=" FMT_WORD ": mem: %s " FMT_PADDR "; <- data=" FMT_WORD, pc, "R", addr, data);
    }
  } else {
    Log("pc=" FMT_WORD ": mem: %s " FMT_PADDR "; data=" FMT_WORD "; mask=0x%02hhx",
        pc,
        "W",
        addr,
        data,
        mask);
  }
}
#endif

static void out_of_bound(paddr_t addr) {
  panic("out of bound: pc=" FMT_WORD ", " FMT_PADDR, static_cast<word_t>(dut_dpi_state.pc), addr);
}

static word_t pmem_read(paddr_t addr) {
  return do_mrom_read(mrom_guest_to_host(addr));
}
