#ifndef RISCV_H__
#define RISCV_H__

#include <stdint.h>

static inline uint8_t inb(uintptr_t addr) {
  return *(volatile uint8_t *)addr;
}
static inline uint16_t inw(uintptr_t addr) {
  return *(volatile uint16_t *)addr;
}
static inline uint32_t inl(uintptr_t addr) {
  return *(volatile uint32_t *)addr;
}

static inline void outb(uintptr_t addr, uint8_t data) {
  *(volatile uint8_t *)addr = data;
}
static inline void outw(uintptr_t addr, uint16_t data) {
  *(volatile uint16_t *)addr = data;
}
static inline void outl(uintptr_t addr, uint32_t data) {
  *(volatile uint32_t *)addr = data;
}

#define PTE_V 0x01
#define PTE_R 0x02
#define PTE_W 0x04
#define PTE_X 0x08
#define PTE_U 0x10
#define PTE_A 0x40
#define PTE_D 0x80

typedef union Pte_ {
  struct {
    uintptr_t v : 1;
    uintptr_t r : 1;
    uintptr_t w : 1;
    uintptr_t x : 1;
    uintptr_t u : 1;
    uintptr_t g : 1;
    uintptr_t a : 1;
    uintptr_t d : 1;
    uintptr_t rsw : 2;
#ifdef __ISA_RISCV64__
    uintptr_t ppn0 : 9;
    uintptr_t ppn1 : 9;
    uintptr_t ppn2 : 26;
    uintptr_t resv0_ : 7;
    uintptr_t pbmt : 2;
    uintptr_t n : 1;
#else
    uintptr_t ppn0 : 10;
    uintptr_t ppn1 : 12;
#endif
  };
  struct {
    uintptr_t flags : 8;
    uintptr_t : 2;
#ifdef __ISA_RISCV64__
    uintptr_t ppn : 44;
#else
    uintptr_t ppn : 22;
#endif
  };
  uintptr_t packed;
} Pte_t;

typedef union Vaddr_ {
  struct {
    uintptr_t offset : 12;
#ifdef __ISA_RISCV64__
    uintptr_t vpn0 : 9;
    uintptr_t vpn1 : 9;
    uintptr_t vpn2 : 9;
#else
    uintptr_t vpn0 : 10;
    uintptr_t vpn1 : 10;
#endif
  };
  struct {
    uintptr_t : 12;
#ifdef __ISA_RISCV64__
    uintptr_t vpn : 27;
#else
    uintptr_t vpn : 20;
#endif
  };
  uintptr_t packed;
} Vaddr_t;

typedef union Paddr_ {
  struct {
    uintptr_t offset : 12;
#ifdef __ISA_RISCV64__
    uintptr_t ppn0 : 9;
    uintptr_t ppn1 : 9;
    uintptr_t ppn2 : 26;
#else
    uintptr_t ppn0 : 10;
    uintptr_t ppn1 : 12;
#endif
  };
  struct {
    uintptr_t : 12;
#ifdef __ISA_RISCV64__
    uintptr_t ppn : 44;
#else
    uintptr_t ppn : 22;
#endif
  };
  uintptr_t packed;
} Paddr_t;

enum { MODE_U, MODE_S, MODE_M = 3 };
#define MSTATUS_MXR (1 << 19)
#define MSTATUS_SUM (1 << 18)

#if __riscv_xlen == 64
#define MSTATUS_SXL (2ull << 34)
#define MSTATUS_UXL (2ull << 32)
#else
#define MSTATUS_SXL 0
#define MSTATUS_UXL 0
#endif

#endif
