#ifndef ARCH_H__
#define ARCH_H__

#include <stdint.h>

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
#define GPR2 gpr[10] // a0
#define GPR3 gpr[11] // a1
#define GPR4 gpr[12] // a2
// Return value
#define GPRx gpr[10] // a0

enum {
  INTR_S_SOFT = 1,
  INTR_M_SOFT = 3,
  INTR_S_TIMR = 5,
  INTR_M_TIMR = 7,
  INTR_S_EXTN = 9,
  INTR_M_EXTN = 11,
};

enum {
  EXCP_INST_UNALIGNED = 0,
  EXCP_INST_ACCESS = 1,
  EXCP_INST = 2,
  EXCP_BREAK = 3,
  EXCP_READ_UNALIGNED = 4,
  EXCP_READ_ACCESS = 5,
  EXCP_STORE_UNALIGNED = 6,
  EXCP_STORE_ACCESS = 7,
  EXCP_U_ENV_CALL = 8,
  EXCP_S_ENV_CALL = 9,
  EXCP_M_ENV_CALL = 11,
  EXCP_INST_PAGE = 12,
  EXCP_READ_PAGE = 13,
  EXCP_STORE_PAGE = 14,
};

#endif
