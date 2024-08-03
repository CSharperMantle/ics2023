#ifndef NPC_HOST_HPP_
#define NPC_HOST_HPP_

#include <cstddef>
#include <cstdint>

#include "common.hpp"

constexpr size_t MROM_SIZE = 0x1000;
constexpr size_t MROM_LEFT = 0x20000000;
constexpr size_t MROM_RIGHT = MROM_LEFT + MROM_SIZE - 1;
constexpr size_t RESET_VECTOR = MROM_LEFT + 0;

constexpr size_t FLASH_SIZE = 0x1000;
constexpr size_t FLASH_LEFT = 0x30000000;
constexpr size_t FLASH_RIGHT = FLASH_LEFT + FLASH_SIZE - 1;

using paddr_t = word_t;
#define FMT_PADDR FMT_WORD

extern uint8_t mrom[MROM_SIZE];
extern uint8_t flash[FLASH_SIZE];

constexpr void *mrom_guest_to_host(word_t paddr) {
  return &mrom[paddr - MROM_LEFT];
}

constexpr word_t mrom_host_to_guest(uint8_t *haddr) {
  return haddr - &mrom[0] + MROM_LEFT;
}

constexpr void *flash_guest_to_host(word_t paddr) {
  return &flash[paddr];
}

constexpr word_t flash_host_to_guest(uint8_t *haddr) {
  return haddr - &flash[0];
}

extern uint8_t flash[FLASH_SIZE];

word_t do_mrom_read(void *addr);
word_t do_flash_read(void *addr);

#endif /* NPC_HOST_HPP_ */