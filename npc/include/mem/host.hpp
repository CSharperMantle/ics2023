#ifndef NPC_HOST_HPP_
#define NPC_HOST_HPP_

#include <cstddef>
#include <cstdint>

#include "common.hpp"

constexpr size_t PMEM_SIZE = 0x8000000;
constexpr size_t PMEM_LEFT = 0x80000000;
constexpr size_t PMEM_RIGHT = PMEM_LEFT + PMEM_SIZE - 1;
constexpr size_t RESET_VECTOR = PMEM_LEFT + 0;

using paddr_t = word_t;
#define FMT_PADDR FMT_WORD

extern std::uint8_t host_mem[PMEM_SIZE];

constexpr void *guest_to_host(word_t paddr) {
  return &host_mem[paddr - PMEM_LEFT];
}

constexpr word_t host_to_guest(uint8_t *haddr) {
  return haddr - &host_mem[0] + PMEM_LEFT;
}

word_t host_read(void *addr);
void host_write(void *addr, std::uint8_t mask, word_t data);

#endif /* NPC_HOST_HPP_ */