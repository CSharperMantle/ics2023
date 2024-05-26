#ifndef NPC_COMMON_HPP_
#define NPC_COMMON_HPP_

#include <cstdint>

#include "config.hpp"
#include "macro.hpp"

typedef MUXDEF(CONFIG_ISA64, std::uint64_t, std::uint32_t) word_t;
constexpr size_t XLEN = MUXDEF(CONFIG_ISA64, 64, 32);
#define FMT_WORD     MUXDEF(CONFIG_ISA64, "0x%016" PRIx64, "0x%08" PRIx32)
#define FMT_WORD_INT MUXDEF(CONFIG_ISA64, "%" PRId64, "%" PRId32)

#endif /* NPC_COMMON_HPP_ */