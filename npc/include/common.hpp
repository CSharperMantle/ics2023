#ifndef NPC_COMMON_HPP_
#define NPC_COMMON_HPP_

#include <cinttypes>
#include <cstdint>

#include "config.hpp"
#include "macro.hpp"

#ifdef CONFIG_ISA64
using word_t = uint64_t using sword_t = int64_t constexpr size_t XLEN = 64
#define FMT_WORD     "0x%016" PRIx64
#define FMT_WORD_INT "%" PRId64
#else
using word_t = uint32_t;
using sword_t = int32_t;
constexpr size_t XLEN = 32;
#define FMT_WORD     "0x%08" PRIx32
#define FMT_WORD_INT "%" PRId32
#endif

#endif /* NPC_COMMON_HPP_ */