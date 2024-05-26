#ifndef NPC_PMEM_HPP_
#define NPC_PMEM_HPP_

#include "common.hpp"
#include <cstddef>
#include <cstdint>

constexpr size_t PMEM_SIZE = 0x8000000;
constexpr size_t PMEM_LEFT = 0x80000000;
constexpr size_t PMEM_RIGHT = PMEM_LEFT + PMEM_SIZE - 1;
constexpr size_t RESET_VECTOR = PMEM_LEFT + 0;

using paddr_t = word_t;
using vaddr_t = word_t;

extern std::uint8_t pmem[PMEM_SIZE];

uint8_t *guest_to_host(word_t paddr) noexcept;
word_t host_to_guest(uint8_t *haddr) noexcept;

#endif /* NPC_PMEM_HPP_ */