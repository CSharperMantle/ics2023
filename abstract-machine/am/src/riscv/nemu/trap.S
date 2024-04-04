#define concat_temp(x, y) x ## y
#define concat(x, y) concat_temp(x, y)
#define MAP(c, f) c(f)

#define PRIV_MODE_U 0
#define PRIV_MODE_S 1
#define PRIV_MODE_M 3

#if __riscv_xlen == 32
#define LOAD  lw
#define STORE sw
#define XLEN  4
#else
#define LOAD  ld
#define STORE sd
#define XLEN  8
#endif

#define REGS_LO16(f) \
      f( 1)       f( 3) f( 4) f( 5) f( 6) f( 7) f( 8) f( 9) \
f(10) f(11) f(12) f(13) f(14) f(15)
#ifndef __riscv_e
#define REGS_HI16(f) \
                                    f(16) f(17) f(18) f(19) \
f(20) f(21) f(22) f(23) f(24) f(25) f(26) f(27) f(28) f(29) \
f(30) f(31)
#define NR_REGS 32
#else
#define REGS_HI16(f)
#define NR_REGS 16
#endif

#define REGS(f) REGS_LO16(f) REGS_HI16(f)

#define PUSH(n) STORE concat(x, n), (n * XLEN)(sp);
#define POP(n)  LOAD  concat(x, n), (n * XLEN)(sp);

#define CONTEXT_SIZE    ((NR_REGS + 3 + 2) * XLEN)

#define OFFSET_SP       (2 * XLEN)
#define OFFSET_CAUSE    ((NR_REGS + 0) * XLEN)
#define OFFSET_STATUS   ((NR_REGS + 1) * XLEN)
#define OFFSET_EPC      ((NR_REGS + 2) * XLEN)
#define OFFSET_NP       ((NR_REGS + 4) * XLEN)

.align 3
.globl __am_asm_trap
__am_asm_trap:
  csrrw sp, mscratch, sp  # (1) atomically exchange `sp` and `mscratch` (aka `ksp`)
  bnez sp, L_SAVE_CTX     # (2) take the branch if we trapped from user
  csrrw sp, mscratch, sp  # (3) if we trapped from kernel, restore the original `sp`

L_SAVE_CTX:
  addi sp, sp, -CONTEXT_SIZE

  MAP(REGS_LO16, PUSH)
  
  # c->np = (ksp == 0) ? KERNEL : USER;
  csrr t0, mscratch
  beqz t0, L_SAVE_KERNEL

  li t0, PRIV_MODE_U
  csrr t1, mscratch  # in this case, original running stack is in `mscratch`
  j L_SAVE_HI16

L_SAVE_KERNEL:
  li t0, PRIV_MODE_M
  mv t1, sp  # in this case, original running stack is in `sp` with `-CONTEXT_SIZE` added
  addi t1, t1, CONTEXT_SIZE

L_SAVE_HI16:
  STORE t0, OFFSET_NP(sp)
  STORE t1, OFFSET_SP(sp)

  # ksp = 0;
  csrw mscratch, zero

  MAP(REGS_HI16, PUSH)

  csrr t0, mcause
  csrr t1, mstatus
  csrr t2, mepc

  STORE t0, OFFSET_CAUSE(sp)
  STORE t1, OFFSET_STATUS(sp)
  STORE t2, OFFSET_EPC(sp)

  # set mstatus.MPRV to pass difftest
  li a0, (1 << 17)
  or t1, t1, a0
  csrw mstatus, t1

  mv a0, sp
  jal __am_get_cur_as
  jal __am_irq_handle
  jal __am_switch
  mv sp, a0
  
  # `if (c->np != USER)` ...
  LOAD t0, OFFSET_NP(sp)
  li t1, PRIV_MODE_U
  bne t0, t1, L_RESTORE_CTX  # ... then we restore original `sp` from context

  # otherwise, we run `ksp = $sp` first
  addi t0, sp, CONTEXT_SIZE
  csrw mscratch, t0
  
L_RESTORE_CTX:
  LOAD t1, OFFSET_STATUS(sp)
  LOAD t2, OFFSET_EPC(sp)
  csrw mstatus, t1
  csrw mepc, t2

  MAP(REGS_HI16, POP)
  MAP(REGS_LO16, POP)

  LOAD sp, OFFSET_SP(sp)
  mret
