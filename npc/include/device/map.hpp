#ifndef NPC_DEVICE_MAP_HPP_
#define NPC_DEVICE_MAP_HPP_

#include <algorithm>
#include <cstdint>
#include <functional>
#include <vector>

#include "common.hpp"
#include "mem/paddr.hpp"

using io_callback_t = std::function<void(uint32_t, uint8_t, bool)>;
uint8_t *new_space(int size);

struct IOMap {
  const char *name;
  // we treat ioaddr_t as paddr_t here
  paddr_t low;
  paddr_t high;
  void *space;
  io_callback_t callback;
};

constexpr bool map_inside(const IOMap &map, paddr_t addr) {
  return (addr >= map.low && addr <= map.high);
}

void init_map();
int find_mapid_by_addr(const std::vector<IOMap> &maps, paddr_t addr);
void add_mmio_map(
    const char *name, paddr_t addr, void *space, uint32_t len, io_callback_t callback);
word_t map_read(paddr_t addr, IOMap *map);
void map_write(paddr_t addr, uint8_t mask, word_t data, IOMap *map);

#endif /* NPC_DEVICE_MAP_HPP_ */
