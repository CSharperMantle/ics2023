#include "pmem.hpp"
#include <array>
#include <cstdint>

std::uint8_t pmem[PMEM_SIZE] = {0};

uint8_t *guest_to_host(word_t paddr) noexcept {
  return &pmem[paddr - PMEM_LEFT];
}

word_t host_to_guest(uint8_t *haddr) noexcept {
  return haddr - &pmem[0] + PMEM_LEFT;
}
