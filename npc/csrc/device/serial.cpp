#include <cstdint>
#include <cstdio>

#include "common.hpp"
#include "debug.hpp"
#include "device/map.hpp"

/* http://en.wikibooks.org/wiki/Serial_Programming/8250_UART_Programming */
// NOTE: this is compatible to 16550

#define CH_OFFSET 0

static uint8_t *serial_base = NULL;

static void serial_io_handler(uint32_t offset, uint8_t mask, bool is_write) {
  const uint64_t buf = *reinterpret_cast<uint64_t *>(serial_base);

  switch (offset) {
    case CH_OFFSET:
      assert(is_write);
      for (size_t i = 0; i < 8; i++) {
        if ((mask >> i) & 0b1) {
          std::putchar((buf >> (i * 8)) & 0xFF);
          std::fflush(stdout);
        }
      }
      break;
    default: panic("do not support offset = %d", offset);
  }
}

void init_serial() {
  serial_base = new_space(8);
  add_mmio_map("serial", CONFIG_SERIAL_MMIO, serial_base, 8, serial_io_handler);
}
