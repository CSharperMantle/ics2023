#include <cassert>
#include <cstdint>
#include <memory>

#include "common.hpp"
#include "debug.hpp"
#include "device/map.hpp"
#include "difftest.hpp"
#include "dpi.hpp"
#include "mem/host.hpp"
#include "mem/paddr.hpp"

#define IO_SPACE_MAX (2 * 1024 * 1024)

static uint8_t *io_space = nullptr;
static uint8_t *p_space = nullptr;

uint8_t *new_space(int size) {
  uint8_t *p = p_space;
  // page aligned;
  size = (size + (PAGE_SIZE - 1)) & ~PAGE_MASK;
  p_space += size;
  assert(p_space - io_space < IO_SPACE_MAX);
  return p;
}

static void check_bound(IOMap *map, paddr_t addr) {
  Assert(map != nullptr,
         "address (" FMT_PADDR ") is out of bound at pc = " FMT_WORD,
         addr,
         static_cast<word_t>(dut_dpi_state.pc));
  Assert(addr <= map->high && addr >= map->low,
         "address (" FMT_PADDR ") is out of bound {%s} [" FMT_PADDR ", " FMT_PADDR
         "] at pc = " FMT_WORD,
         addr,
         map->name,
         map->low,
         map->high,
         static_cast<word_t>(dut_dpi_state.pc));
}

static void invoke_callback(io_callback_t c, paddr_t offset, uint8_t mask, bool is_write) {
  if (c) {
    c(offset, mask, is_write);
  }
}

#ifdef CONFIG_DTRACE
static void print_dtrace(paddr_t addr, const char *name, uint8_t mask, bool read) {
  Log("dev: %s " FMT_PADDR " (in \"%s\"); mask=0x%02hhx", read ? "R" : "W", addr, name, mask);
}
#endif

int find_mapid_by_addr(const std::vector<IOMap> &maps, paddr_t addr) {
  extern std::unique_ptr<DiffTest> difftest;
  for (size_t i = 0; i < maps.size(); i++) {
    if (map_inside(maps[i], addr)) {
      difftest->skip_next();
      return i;
    }
  }
  return -1;
}

void init_map() {
  io_space = new uint8_t[IO_SPACE_MAX];
  p_space = io_space;
}

word_t map_read(paddr_t addr, IOMap *map) {
#ifdef CONFIG_DTRACE
  print_dtrace(addr, map->name, 0, true);
#endif
  check_bound(map, addr);
  paddr_t offset = addr - map->low;
  invoke_callback(map->callback, offset, 0, false); // prepare data to read
  word_t ret = host_read(reinterpret_cast<uint8_t *>(map->space) + offset);
  return ret;
}

void map_write(paddr_t addr, uint8_t mask, word_t data, IOMap *map) {
#ifdef CONFIG_DTRACE
  print_dtrace(addr, map->name, mask, false);
#endif
  check_bound(map, addr);
  paddr_t offset = addr - map->low;
  host_write(reinterpret_cast<uint8_t *>(map->space) + offset, mask, data);
  invoke_callback(map->callback, offset, mask, true);
}
