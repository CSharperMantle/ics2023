#ifndef ARCH_H__
#define ARCH_H__

#ifdef __riscv_e
#define NR_REGS 16
#else
#define NR_REGS 32
#endif

struct Context {
  uintptr_t gpr[NR_REGS];
  uintptr_t mcause;
  uintptr_t mstatus;
  uintptr_t mepc;
  void *pdir;
};

#ifdef __riscv_e
#define GPR1 gpr[15] // a5
#else
#define GPR1 gpr[17] // a7
#endif

// Argument passing
#define GPR2 gpr[4] // a0
#define GPR3 gpr[5] // a1
#define GPR4 gpr[6] // a2
// Return value
#define GPRx gpr[4] // a0

#endif
