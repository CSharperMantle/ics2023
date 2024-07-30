#ifndef IRINGBUF_H_INCLUDED_
#define IRINGBUF_H_INCLUDED_

#include <utility>

#include "common.hpp"
#include "mem/paddr.hpp"
#include "util/ringbuf.hpp"

using IRingBuf = baudvine::RingBuf<std::tuple<paddr_t, uint32_t, uint8_t>, CONFIG_IRINGBUF_NR_ELEM>;

extern IRingBuf iringbuf;

#endif /* IRINGBUF_H_INCLUDED_ */