#ifndef IRINGBUF_H_INCLUDED_
#define IRINGBUF_H_INCLUDED_

#include <common.h>
#include <string.h>

#ifndef CONFIG_IRINGBUF_NR_ELEM
#define CONFIG_IRINGBUF_NR_ELEM 1
#endif

#define IRINGBUF_ITER_BEGIN(ident_) do {
#define IRINGBUF_ITER_END(ident_)                                                                  \
  ident_ = (ident_ + 1) % CONFIG_IRINGBUF_NR_ELEM;                                                 \
  }                                                                                                \
  while (ident_ != iringbuf.head)                                                                  \
    ;

typedef struct IRingBuf_Inst_ {
  vaddr_t addr;
  uint32_t inst_val;
} IRingBuf_Inst_t;

typedef struct IRingBuf_ {
  IRingBuf_Inst_t insts[CONFIG_IRINGBUF_NR_ELEM + 1];
  size_t head;
  size_t tail;
  bool full;
} IRingBuf_t;

extern IRingBuf_t iringbuf;

void iringbuf_insert(vaddr_t addr, uint32_t inst_val);

#endif /* IRINGBUF_H_INCLUDED_ */