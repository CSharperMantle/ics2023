#include "VTop.h"
#include "common.hpp"
#include "debug.hpp"
#include "difftest.hpp"
#include "mem/host.hpp"
#include "mem/paddr.hpp"
#include "util/iringbuf.hpp"

extern VTop dut;
extern IRingBuf iringbuf;

static word_t ifu_request_addr = 0;

extern "C" bool npc_dpi_ifu(sword_t pc, int *instr) {
  const word_t upc = static_cast<word_t>(pc) & ~0x3u;
  if (ifu_request_addr != upc) {
    *instr = 0xdead3210;
    ifu_request_addr = upc;
    return false;
  } else {
    const uint32_t instr_ = paddr_read(static_cast<paddr_t>(upc));
    instr_pending = std::make_pair(upc, instr_);
    *instr = instr_;
    return true;
  }
}

extern "C" word_t npc_dpi_pmem_read(sword_t mem_r_addr) {
  const word_t r_addr = static_cast<word_t>(mem_r_addr) & ~0x3u;
  return paddr_read(r_addr);
}

extern "C" void npc_dpi_pmem_write(char mem_mask, word_t mem_w_addr, word_t mem_w_data) {
  const word_t w_addr = static_cast<word_t>(mem_w_addr) & ~0x3u;
  paddr_write(w_addr, mem_mask, mem_w_data);
}