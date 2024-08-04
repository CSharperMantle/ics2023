#ifndef NPC_PMEM_HPP_
#define NPC_PMEM_HPP_

#include "mem/host.hpp"

constexpr size_t PAGE_SHIFT = 12;
constexpr size_t PAGE_SIZE = 1ul << PAGE_SHIFT;
constexpr size_t PAGE_MASK = PAGE_SIZE - 1;

constexpr bool in_mrom(paddr_t addr) {
  return addr - MROM_LEFT < MROM_SIZE;
}

constexpr bool in_flash(paddr_t addr) {
  return addr - FLASH_LEFT < FLASH_SIZE;
}

#endif /* NPC_PMEM_HPP_ */