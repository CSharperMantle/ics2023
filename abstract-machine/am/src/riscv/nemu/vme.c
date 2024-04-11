#include <am.h>
#include <klib.h>
#include <nemu.h>

static AddrSpace kas = {};
static void *(*pgalloc_usr)(int) = NULL;
static void (*pgfree_usr)(void *) = NULL;
static int vme_enable = 0;

// Kernel memory mappings
static Area segments[] = {NEMU_PADDR_SPACE};

#define USER_SPACE RANGE(0x40000000, 0x80000000)

static inline void set_satp(void *pdir) {
#ifdef __ISA_RISCV64__
  const CsrSatp_t satp = {.mode = MEM_PAGING_SV39, .ppn = (uintptr_t)pdir >> 12};
#else
  const CsrSatp_t satp = {.mode = MEM_PAGING_SV32, .ppn = (uintptr_t)pdir >> 12};
#endif
  asm volatile("csrw satp, %0" : : "r"(satp.packed));
}

static inline uintptr_t get_satp() {
  CsrSatp_t satp;
  asm volatile("csrr %0, satp" : "=r"(satp.packed));
  return satp.ppn << 12;
}

bool vme_init(void *(*pgalloc_f)(int), void (*pgfree_f)(void *)) {
  pgalloc_usr = pgalloc_f;
  pgfree_usr = pgfree_f;

  kas.ptr = pgalloc_f(PGSIZE);
  assert(kas.ptr != NULL);

  for (size_t i = 0; i < LENGTH(segments); i++) {
    void *va = segments[i].start;
    for (; va < segments[i].end; va += PGSIZE) {
      map(&kas, va, va, PTE_R | PTE_W | PTE_X | PTE_U | PTE_G);
    }
  }

  set_satp(kas.ptr);
  vme_enable = 1;

  return true;
}

void protect(AddrSpace *as) {
  PTE *updir = (PTE *)(pgalloc_usr(PGSIZE));
  as->ptr = updir;
  as->area = USER_SPACE;
  as->pgsize = PGSIZE;
  // map kernel space
  memcpy(updir, kas.ptr, PGSIZE);
}

void unprotect(AddrSpace *as) {}

Context *__am_get_cur_as(Context *c) {
  c->pdir = (vme_enable ? (void *)get_satp() : NULL);
  return c;
}

Context *__am_switch(Context *c) {
  if (vme_enable && c->pdir != NULL && c->pdir != kas.ptr) { // TODO: Hack, fix this last comp
    set_satp(c->pdir);
  }
  return c;
}

void map(AddrSpace *as, void *va, void *pa, int prot) {
#ifndef __ISA_RISCV64__
  assert(((void)"mapping only implemented for RV64", 0));
#else
  assert(as->ptr != NULL);

  const Paddr_t pa_ = {.packed = (uintptr_t)pa};
  const Vaddr_t va_ = {.packed = (uintptr_t)va};

  Pte_t *const pt_2 = (Pte_t *)as->ptr;
  Pte_t *const pte_2 = &pt_2[va_.vpn2];
  if (!pte_2->v) {
    pte_2->flags = prot;
    pte_2->ppn = (uintptr_t)pgalloc_usr(PGSIZE) >> 12;
    pte_2->v = 1;
  }

  Pte_t *const pt_1 = (Pte_t *)((uintptr_t)pte_2->ppn << 12);
  Pte_t *const pte_1 = &pt_1[va_.vpn1];
  if (!pte_1->v) {
    pte_1->flags = prot;
    pte_1->ppn = (uintptr_t)pgalloc_usr(PGSIZE) >> 12;
    pte_1->v = 1;
  }

  Pte_t *const pt_0 = (Pte_t *)((uintptr_t)pte_1->ppn << 12);
  Pte_t *const pte_0 = &pt_0[va_.vpn0];
  if (!pte_0->v) {
    pte_0->flags = prot;
    pte_0->ppn = pa_.ppn;
    pte_0->v = 1;
  } else {
    assert(((void)"remap existing address", 0));
  }
#endif
}

Context *ucontext(AddrSpace *as, Area kstack, void *entry) {
  Context *const ctx = (Context *)kstack.end - 1;
  const CsrMstatus_t mstatus = {
      .mpp = PRIV_MODE_U,
#ifdef __ISA_RISCV64__
      .resv_5 = 0x1400,
#endif
      .mpie = 1,
      .mie = 0,
      .sum = 1,
      .mxr = 1,
  };
  ctx->mstatus = mstatus.packed;
  ctx->mepc = (uintptr_t)entry - 4;
  // ctx->GPRx = (uintptr_t)heap.end; // user-mode stack base; to be set by OS.
  ctx->GPRx = (uintptr_t)kstack.end;
  ctx->pdir = as->ptr;
  ctx->np = PRIV_MODE_U;
  return ctx;
}
