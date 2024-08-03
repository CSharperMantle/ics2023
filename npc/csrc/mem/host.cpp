
#include <array>
#include <cstdint>

#include "debug.hpp"
#include "mem/host.hpp"

uint8_t mrom[MROM_SIZE] = {0};
uint8_t flash[FLASH_SIZE] = {0};

word_t do_mrom_read(void *addr) {
  return *reinterpret_cast<word_t *>(addr);
}

word_t do_flash_read(void *addr) {
  return *reinterpret_cast<word_t *>(addr);
}
