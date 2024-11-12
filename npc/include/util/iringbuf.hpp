#ifndef IRINGBUF_H_INCLUDED_
#define IRINGBUF_H_INCLUDED_

#include <utility>

#include "common.hpp"
#include "dpi.hpp"
#include "mem/paddr.hpp"
#include "util/ringbuf.hpp"

using InstrRingBuf =
    baudvine::RingBuf<std::tuple<paddr_t, uint32_t, uint16_t>, CONFIG_IRINGBUF_NR_ELEM>;

extern InstrRingBuf iringbuf;

#endif /* IRINGBUF_H_INCLUDED_ */