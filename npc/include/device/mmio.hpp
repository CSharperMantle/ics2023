#ifndef NPC_DEVICE_MMIO_HPP_
#define NPC_DEVICE_MMIO_HPP_

#include <cstdint>

#include "common.hpp"
#include "mem/paddr.hpp"

word_t mmio_read(paddr_t addr);
void mmio_write(paddr_t addr, uint8_t mask, word_t data);

#endif /* NPC_DEVICE_MMIO_HPP_ */