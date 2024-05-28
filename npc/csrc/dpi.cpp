#include "VTop.h"
#include "common.hpp"
#include "debug.hpp"
#include "difftest.hpp"
#include "pmem.hpp"

extern VTop dut;

extern "C" void npc_dpi_ifu(sword_t pc, int *instr) {
  const word_t upc = static_cast<word_t>(pc) & ~0x3u;
#ifdef CONFIG_MTRACE
  Log("IF " FMT_WORD, upc);
#endif
  if (upc > PMEM_LEFT && upc <= PMEM_RIGHT - 4) {
    *instr = *reinterpret_cast<uint32_t *>(guest_to_host((upc)));
  } else {
    *instr = 0;
  }
}

extern "C" word_t npc_dpi_pmem_read(sword_t mem_r_addr) {
  const word_t r_addr = static_cast<word_t>(mem_r_addr) & ~0x3u;
#ifdef CONFIG_MTRACE
  Log("RD " FMT_WORD, r_addr);
#endif
  return *reinterpret_cast<word_t *>(guest_to_host((r_addr)));
}

extern "C" void npc_dpi_pmem_write(char mem_mask, word_t mem_w_addr, word_t mem_w_data) {
  const word_t w_addr = static_cast<word_t>(mem_w_addr) & ~0x3u;
#ifdef CONFIG_MTRACE
  Log("WR " FMT_WORD "; data=" FMT_WORD " [mask=0x%02hhx]", w_addr, mem_w_data, mem_mask);
#endif
  const word_t byte_mask = ((BITS(mem_mask, 0, 0) ? static_cast<word_t>(0xffu) : 0u) << 0)
                           | ((BITS(mem_mask, 1, 1) ? static_cast<word_t>(0xffu) : 0u) << 8)
                           | ((BITS(mem_mask, 2, 2) ? static_cast<word_t>(0xffu) : 0u) << 16)
                           | ((BITS(mem_mask, 3, 3) ? static_cast<word_t>(0xffu) : 0u) << 24);
  word_t word = *reinterpret_cast<uint32_t *>(guest_to_host(mem_w_addr));
  word &= ~byte_mask;
  word |= mem_w_data & byte_mask;
  *reinterpret_cast<uint32_t *>(guest_to_host(mem_w_addr)) = word;
}