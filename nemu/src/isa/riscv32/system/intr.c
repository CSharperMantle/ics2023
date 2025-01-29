/***************************************************************************************
 * Copyright (c) 2014-2022 Zihao Yu, Nanjing University
 *
 * NEMU is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 *
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
 * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
 * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 *
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/

#include <isa.h>

word_t isa_raise_intr(word_t NO, vaddr_t epc) {
  /* Trigger an interrupt/exception with ``NO''.
   * Then return the address of the interrupt/exception vector.
   */
  const word_t vector = csr(CSR_IDX_MTVEC);
  CsrMstatus_t mstatus = {.packed = csr(CSR_IDX_MSTATUS)};
#ifdef CONFIG_ETRACE
  const CsrMcause_t mcause = {.packed = NO};
  Log("%s " FMT_WORD "; mepc=" FMT_WORD "; mtvec=" FMT_WORD "; mstatus=" FMT_WORD,
      mcause.intr ? "INTR" : "EXCP",
      mcause.code,
      epc,
      vector,
      mstatus.packed);
#endif
  mstatus.mpie = mstatus.mie;
  mstatus.mie = 0;
  mstatus.mpp = cpu.priv;
  csr(CSR_IDX_MSTATUS) = mstatus.packed;
  cpu.priv = PRIV_MODE_M;
  csr(CSR_IDX_MCAUSE) = NO;
  csr(CSR_IDX_MEPC) = epc;
  return vector;
}

word_t isa_query_intr(void) {
  const CsrMstatus_t mstatus = {.packed = csr(CSR_IDX_MSTATUS)};
  if (cpu.intr && mstatus.mie) {
    cpu.intr = false;
    return ((CsrMcause_t){.intr = true, .code = INTR_M_TIMR}).packed;
  }
  return INTR_EMPTY;
}
