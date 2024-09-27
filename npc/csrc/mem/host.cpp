
#include <array>
#include <cstdint>
#include <tuple>

#include "debug.hpp"
#include "mem/host.hpp"

uint8_t mrom[MROM_SIZE] = {0};
uint8_t flash[FLASH_SIZE] = {0};
uint8_t psram[PSRAM_SIZE] = {0};

const std::array<std::tuple<const char *, word_t, size_t>, 5> REF_MEM_BACKED_AREAS = {
    std::make_tuple("sram", 0x0f000000, 0x01000000),
    std::make_tuple("mrom", 0x20000000, 0x00001000),
    std::make_tuple("flash", 0x30000000, 0x10000000),
    std::make_tuple("psram", 0x80000000, 0x20000000),
    std::make_tuple("sdram", 0xa0000000, 0x20000000),
};

word_t do_mrom_read(void *addr) {
  return *reinterpret_cast<word_t *>(addr);
}

word_t do_flash_read(void *addr) {
  return *reinterpret_cast<word_t *>(addr);
}

uint32_t do_psram_read(void *addr) {
  return *reinterpret_cast<uint32_t *>(addr);
}

void do_psram_write(void *addr, uint32_t data) {
  *reinterpret_cast<uint32_t *>(addr) = data;
}
