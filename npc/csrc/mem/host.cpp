
#include <array>
#include <cstdint>

#include "debug.hpp"
#include "mem/host.hpp"

std::uint8_t host_mem[PMEM_SIZE] = {0};

word_t host_read(void *addr) {
  return *reinterpret_cast<word_t *>(addr);
}

void host_write(void *addr, std::uint8_t mask, word_t data) {
  static_assert(sizeof(word_t) == sizeof(uint32_t), "Not implemented for RV64");
  word_t byte_mask = 0;
  for (size_t i = 0; i < sizeof(byte_mask); i++) {
    if ((mask >> i) & 0b1) {
      byte_mask |= 0xFF << (i * 8);
    }
  }
  word_t word = *reinterpret_cast<word_t *>(addr);
  word &= ~byte_mask;
  word |= data & byte_mask;
  Log("0x%02hhx", mask);
  *reinterpret_cast<word_t *>(addr) = word;
}
