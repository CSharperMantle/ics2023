#ifndef NPC_COMMON_HPP_
#define NPC_COMMON_HPP_

#include <cinttypes>
#include <cstdint>

#include "config.hpp"
#include "macro.hpp"

using word_t = MUXDEF(CONFIG_ISA64, std::uint64_t, std::uint32_t);
using sword_t = MUXDEF(CONFIG_ISA64, std::int64_t, std::int32_t);
constexpr size_t XLEN = MUXDEF(CONFIG_ISA64, 64, 32);
#define FMT_WORD     MUXDEF(CONFIG_ISA64, "0x%016" PRIx64, "0x%08" PRIx32)
#define FMT_WORD_INT MUXDEF(CONFIG_ISA64, "%" PRId64, "%" PRId32)

#endif /* NPC_COMMON_HPP_ */