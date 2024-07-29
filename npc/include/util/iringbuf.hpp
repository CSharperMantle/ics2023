#ifndef IRINGBUF_H_INCLUDED_
#define IRINGBUF_H_INCLUDED_

#include <utility>

#include "common.hpp"
#include "mem/paddr.hpp"
#include "util/ringbuf.hpp"

using IRingBuf = baudvine::RingBuf<std::pair<paddr_t, uint32_t>, CONFIG_IRINGBUF_NR_ELEM>;

extern std::pair<paddr_t, uint32_t> instr_pending;
extern IRingBuf iringbuf;

#endif /* IRINGBUF_H_INCLUDED_ */