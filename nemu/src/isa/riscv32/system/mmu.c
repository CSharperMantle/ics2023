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
    default: panic("satp MODE field value unknown");
  }
}

paddr_t isa_mmu_translate(vaddr_t vaddr, int len, int type) {
  const CsrSatp_t satp = {.packed = csr(CSR_IDX_SATP)};
  const Vaddr_t va = {.packed = (word_t)vaddr};

  Pte_t *table = NULL;
  Pte_t *pte = NULL;

  table = (Pte_t *)guest_to_host(satp.ppn << 12);
  Assert(table != NULL, "page table 2 is NULL");
  pte = &table[va.vpn2];
  Assert(pte->v && !(pte->r == 0 && pte->w == 1), "invalid pte %p", pte);

  table = (Pte_t *)guest_to_host(pte->ppn << 12);
  Assert(table != NULL, "page table 1 is NULL");
  pte = &table[va.vpn1];
  Assert(pte->v && !(pte->r == 0 && pte->w == 1), "invalid pte %p", pte);

  table = (Pte_t *)guest_to_host(pte->ppn << 12);
  Assert(table != NULL, "page table 0 is NULL");
  pte = &table[va.vpn0];
  Assert(pte->v && !(pte->r == 0 && pte->w == 1), "invalid pte %p", pte);

  Assert(!(type == MEM_TYPE_READ && !pte->r), "invalid access perm");
  Assert(!(type == MEM_TYPE_WRITE && !pte->w), "invalid access perm");
  Assert(!(type == MEM_TYPE_IFETCH && !pte->x), "invalid access perm");

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
