#include <cpu/iringbuf.h>

IRingBuf_t iringbuf;

void iringbuf_insert(vaddr_t addr, uint32_t inst_val) {
  iringbuf.insts[iringbuf.head] = (IRingBuf_Inst_t){
      .addr = addr,
      .inst_val = inst_val,
  };
  if (iringbuf.full) {
    iringbuf.tail = (iringbuf.tail + 1) % CONFIG_IRINGBUF_NR_ELEM;
  }
  iringbuf.head = (iringbuf.head + 1) % CONFIG_IRINGBUF_NR_ELEM;
  iringbuf.full = iringbuf.head == iringbuf.tail;
}