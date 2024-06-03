#include <array>

#include "common.hpp"
#include "debug.hpp"
#include "device/map.hpp"
#include "mem/paddr.hpp"

constexpr size_t NR_MAP = 16;

static std::vector<IOMap> maps{};

static IOMap *fetch_mmio_map(paddr_t addr) {
  int mapid = find_mapid_by_addr(maps, addr);
  return (mapid == -1 ? NULL : &maps[mapid]);
}

static void report_mmio_overlap(
    const char *name1, paddr_t l1, paddr_t r1, const char *name2, paddr_t l2, paddr_t r2) {
  panic("MMIO region %s@[" FMT_PADDR ", " FMT_PADDR "] is overlapped "
        "with %s@[" FMT_PADDR ", " FMT_PADDR "]",
        name1,
        l1,
        r1,
        name2,
        l2,
        r2);
}

void add_mmio_map(
    const char *name, paddr_t addr, void *space, uint32_t len, io_callback_t callback) {
  paddr_t left = addr, right = addr + len - 1;
  if (in_pmem(left) || in_pmem(right)) {
    report_mmio_overlap(name, left, right, "pmem", PMEM_LEFT, PMEM_RIGHT);
  }
  for (const auto &map : maps) {
    if (left <= map.high && right >= map.low) {
      report_mmio_overlap(name, left, right, map.name, map.low, map.high);
    }
  }
  IOMap map = (IOMap){
      .name = name,
      .low = addr,
      .high = addr + len - 1,
      .space = space,
      .callback = callback,
  };
  maps.push_back(map);
  Log("mmio map: '%s' -> [" FMT_PADDR ", " FMT_PADDR "]", map.name, map.low, map.high);
}

word_t mmio_read(paddr_t addr) {
  return map_read(addr, fetch_mmio_map(addr));
}

void mmio_write(paddr_t addr, uint8_t mask, word_t data) {
  map_write(addr, mask, data, fetch_mmio_map(addr));
}
