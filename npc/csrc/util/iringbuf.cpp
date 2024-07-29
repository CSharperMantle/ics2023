#include "util/iringbuf.hpp"

std::pair<paddr_t, uint32_t> instr_pending;
IRingBuf iringbuf{};
