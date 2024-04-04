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
#include <memory/paddr.h>
#include <memory/vaddr.h>

#ifdef CONFIG_RV64

int isa_mmu_check(vaddr_t vaddr, int len, int type) {
  const CsrSatp_t satp = {.packed = csr(CSR_IDX_SATP)};
  switch (satp.mode) {
    case MEM_PAGING_BARE: return MMU_DIRECT;
    case MEM_PAGING_SV39: return MMU_TRANSLATE;
    default: panic("satp mode unimplemented: %d", satp.mode);
  }
}

#define MMU_ASSERT_(cond_, fmt_, ...)                                                              \
  do {                                                                                             \
    Assert((cond_),                                                                                \
           "%s:%d: mmu error: xlate " FMT_WORD " [len=%d, type=%d]: " fmt_,                        \
           __FUNCTION__,                                                                           \
           __LINE__,                                                                               \
           vaddr,                                                                                  \
           len,                                                                                    \
           type,                                                                                   \
           ##__VA_ARGS__);                                                                         \
  } while (0)

static void check_priv(const Pte_t *pte, vaddr_t vaddr, int len, int type) {
  const CsrMstatus_t mstatus = {.packed = csr(CSR_IDX_MSTATUS)};
  switch (cpu.priv) {
    case PRIV_MODE_M: return;
    case PRIV_MODE_S:
      MMU_ASSERT_(!pte->u || (mstatus.sum && pte->u),
                  "pte " FMT_PADDR ": %s",
                  host_to_guest((void *)pte),
                  pte->u ? "U" : "!U");
      break;
    case PRIV_MODE_U:
      MMU_ASSERT_(pte->u, "pte " FMT_PADDR ": %s", host_to_guest((void *)pte), pte->u ? "U" : "!U");
      break;
    default: panic("unknown priv %d", cpu.priv);
  }
}

static void check_prot(const Pte_t *pte, vaddr_t vaddr, int len, int type) {
  const CsrMstatus_t mstatus = {.packed = csr(CSR_IDX_MSTATUS)};
  switch (type) {
    case MEM_TYPE_READ:
      MMU_ASSERT_(pte->r || (mstatus.mxr && pte->x),
                  "pte " FMT_PADDR ": !R && (!mstatus.MXR || !X)",
                  host_to_guest((void *)pte));
      break;
    case MEM_TYPE_WRITE:
      MMU_ASSERT_(pte->w, "pte " FMT_PADDR ": !W", host_to_guest((void *)pte));
      break;
    case MEM_TYPE_IFETCH:
      MMU_ASSERT_(pte->x, "pte " FMT_PADDR ": !X", host_to_guest((void *)pte));
      break;
    default: panic("unknown type %d", type);
  }
}

paddr_t isa_mmu_translate(vaddr_t vaddr, int len, int type) {
  const CsrSatp_t satp = {.packed = csr(CSR_IDX_SATP)};
  const Vaddr_t va = {.packed = (word_t)vaddr};

  Pte_t *table[3] = {0};
  Pte_t *pte = NULL;

  table[2] = (Pte_t *)guest_to_host(satp.ppn << 12);
  MMU_ASSERT_(table[2] != NULL, "page table 2 is NULL");
  pte = &table[2][va.vpn2];
  MMU_ASSERT_(pte->v, "pte " FMT_PADDR ": !V", host_to_guest((void *)pte));
  MMU_ASSERT_(
      !(pte->r == 0 && pte->w == 1), "pte " FMT_PADDR ": R && !W", host_to_guest((void *)pte));
  check_priv(pte, vaddr, len, type);

  table[1] = (Pte_t *)guest_to_host(pte->ppn << 12);
  MMU_ASSERT_(table != NULL, "page table 1 is NULL");
  pte = &table[1][va.vpn1];
  MMU_ASSERT_(pte->v, "pte " FMT_PADDR ": !V", host_to_guest((void *)pte));
  check_priv(pte, vaddr, len, type);

  table[0] = (Pte_t *)guest_to_host(pte->ppn << 12);
  MMU_ASSERT_(table != NULL, "page table 0 is NULL");
  pte = &table[0][va.vpn0];
  MMU_ASSERT_(pte->v, "pte " FMT_PADDR ": !V", host_to_guest((void *)pte));
  MMU_ASSERT_(
      !(pte->r == 0 && pte->w == 1), "pte " FMT_PADDR ": R && !W", host_to_guest((void *)pte));
  check_priv(pte, vaddr, len, type);

  check_prot(pte, vaddr, len, type);

  if (!pte->a || (type == MEM_TYPE_WRITE && !pte->d)) {
    pte->a = 1;
    if (type == MEM_TYPE_WRITE) {
      pte->d = 1;
    }
  }

  Paddr_t pa = {0};
  pa.offset = va.offset;
  pa.ppn = pte->ppn;

  return pa.packed;
}

#else
int isa_mmu_check(vaddr_t vaddr, int len, int type) {
  panic("unimplemented");
}

paddr_t isa_mmu_translate(vaddr_t vaddr, int len, int type) {
  panic("unimplemented");
}
#endif
