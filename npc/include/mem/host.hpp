#ifndef NPC_HOST_HPP_
#define NPC_HOST_HPP_

#include <array>
#include <cstddef>
#include <cstdint>
#include <tuple>

#include "common.hpp"

constexpr size_t MROM_SIZE = 0x1000;
constexpr size_t MROM_LEFT = 0x20000000;
constexpr size_t MROM_RIGHT = MROM_LEFT + MROM_SIZE - 1;

constexpr size_t FLASH_SIZE = 0x1000000;
constexpr size_t FLASH_LEFT = 0x30000000;
constexpr size_t FLASH_RIGHT = FLASH_LEFT + FLASH_SIZE - 1;

constexpr size_t PSRAM_SIZE = 0x1000000;
constexpr size_t PSRAM_LEFT = 0x80000000;
constexpr size_t PSRAM_RIGHT = PSRAM_LEFT + PSRAM_SIZE - 1;

constexpr size_t RESET_VECTOR = FLASH_LEFT + 0;

using paddr_t = word_t;
#define FMT_PADDR FMT_WORD

extern uint8_t mrom[MROM_SIZE];
extern uint8_t flash[FLASH_SIZE];
extern uint8_t psram[PSRAM_SIZE];

extern const std::array<std::tuple<const char *, word_t, size_t>, 5> REF_MEM_BACKED_AREAS;

constexpr void *mrom_guest_to_host(word_t paddr) {
  return &mrom[paddr - MROM_LEFT];
}

constexpr word_t mrom_host_to_guest(uint8_t *haddr) {
  return haddr - &mrom[0] + MROM_LEFT;
}

constexpr void *flash_guest_to_host(word_t paddr) {
  return &flash[paddr - FLASH_LEFT];
}

constexpr word_t flash_host_to_guest(uint8_t *haddr) {
  return haddr - &flash[0] + FLASH_LEFT;
}

constexpr void *psram_guest_to_host(word_t paddr) {
  return &psram[paddr - PSRAM_LEFT];
}

constexpr word_t psram_host_to_guest(uint8_t *haddr) {
  return haddr - &psram[0] + PSRAM_LEFT;
}

word_t do_mrom_read(void *addr);
word_t do_flash_read(void *addr);
uint32_t do_psram_read(void *addr);
void do_psram_write(void *addr, uint32_t data);

#endif /* NPC_HOST_HPP_ */