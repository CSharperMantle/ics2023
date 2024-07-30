#include "common.hpp"
#include "mem/host.hpp"
#include "mem/paddr.hpp"

extern "C" word_t npc_dpi_pmem_read(sword_t mem_r_addr) {
  const word_t r_addr = static_cast<word_t>(mem_r_addr) & ~0x3u;
  return paddr_read(r_addr);
}

extern "C" void npc_dpi_pmem_write(char mem_mask, word_t mem_w_addr, word_t mem_w_data) {
  const word_t w_addr = static_cast<word_t>(mem_w_addr) & ~0x3u;
  paddr_write(w_addr, mem_mask, mem_w_data);
}
