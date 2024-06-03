#include <array>
#include <cstdint>

#include "VTop.h"
#include "VTop__Syms.h"
#include "common.hpp"
#include "debug.hpp"
#include "mem/paddr.hpp"

extern VTop dut;

#ifdef CONFIG_MTRACE
static void print_mtrace(paddr_t addr, bool read, word_t data, std::uint8_t mask) {
  const word_t pc = static_cast<word_t>(dut.io_pc);
  if (read) {
    Log("pc=" FMT_WORD ": mem: %s; " FMT_PADDR "; data=" FMT_WORD, pc, "R", addr, data);
  } else {
    Log("pc=" FMT_WORD ": mem: %s; " FMT_PADDR "; data=" FMT_WORD "; mask=0x%02hhx",
        pc,
        "W",
        addr,
        data,
        mask);
  }
}
#endif

static void out_of_bound(paddr_t addr) {
  panic("out of bound: pc=" FMT_WORD ", [" FMT_PADDR "]", static_cast<word_t>(dut.io_pc), addr);
}

static word_t pmem_read(paddr_t addr) {
  return host_read(guest_to_host(addr));
}

static void pmem_write(paddr_t addr, std::uint8_t mask, word_t data) {
  host_write(guest_to_host(addr), mask, data);
}

#define mmio_read(...) 0xdeadbeef
#define mmio_write(...)

word_t paddr_read(paddr_t addr) {
  word_t val = 0;
  if (likely(in_pmem(addr))) {
    val = pmem_read(addr);
  } else if (MUXDEF(CONFIG_DEVICE, true, false)) {
    val = mmio_read(addr);
  } else {
    out_of_bound(addr);
  }
#ifdef CONFIG_MTRACE
  print_mtrace(addr, true, val, 0);
#endif
  return val;
}

void paddr_write(paddr_t addr, std::uint8_t mask, word_t data) {
#ifdef CONFIG_MTRACE
  print_mtrace(addr, false, data, mask);
#endif
  if (likely(in_pmem(addr))) {
    pmem_write(addr, mask, data);
  } else if (MUXDEF(CONFIG_DEVICE, true, false)) {
    mmio_write(addr, mask, data);
  } else {
    out_of_bound(addr);
  }
}