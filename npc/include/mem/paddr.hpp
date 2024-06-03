#ifndef NPC_PMEM_HPP_
#define NPC_PMEM_HPP_

#include "mem/host.hpp"

constexpr size_t PAGE_SHIFT = 12;
constexpr size_t PAGE_SIZE = 1ul << PAGE_SHIFT;
constexpr size_t PAGE_MASK = PAGE_SIZE - 1;

constexpr bool in_pmem(paddr_t addr) {
  return addr - PMEM_LEFT < PMEM_SIZE;
}

word_t paddr_read(paddr_t addr);
void paddr_write(paddr_t addr, uint8_t mask, word_t data);

#endif /* NPC_PMEM_HPP_ */