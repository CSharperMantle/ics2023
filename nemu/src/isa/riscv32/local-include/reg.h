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

#ifndef __RISCV_REG_H__
#define __RISCV_REG_H__

#include <common.h>

static inline int check_gpr_idx(size_t idx) {
  IFDEF(CONFIG_RT_CHECK, assert(likely(idx < MUXDEF(CONFIG_RVE, 16, 32))));
  return idx;
}

static inline int check_csr_reg(size_t idx) {
  IFDEF(CONFIG_RT_CHECK, assert(likely(idx < 4096)));
  return idx;
}

#define CSR_IDX_SATP     0x180
#define CSR_IDX_MSTATUS  0x300
#define CSR_IDX_MIE      0x304
#define CSR_IDX_MTVEC    0x305
#define CSR_IDX_MSCRATCH 0x340
#define CSR_IDX_MEPC     0x341
#define CSR_IDX_MCAUSE   0x342
#define CSR_IDX_MTVAL    0x343
#define CSR_IDX_MIP      0x344

typedef union CsrMstatus_ {
  struct {
    word_t resv_0 : 1;
    word_t sie : 1;
    word_t resv_1 : 1;
    word_t mie : 1;
    word_t resv_2 : 1;
    word_t spie : 1;
    word_t resv_3 : 1;
    word_t mpie : 1;
    word_t spp : 1;
    word_t resv_4 : 2;
    word_t mpp : 2;
    word_t fs : 2;
    word_t xs : 2;
    word_t mprv : 1;
    word_t sum : 1;
    word_t mxr : 1;
    word_t tvm : 1;
    word_t tw : 1;
    word_t tsr : 1;
#ifdef CONFIG_RV64
    word_t resv_5 : 40; // XLEN - 24
#else
    word_t resv_5 : 8; // XLEN - 24
#endif
    word_t sd : 1;
  };
  word_t packed;
} CsrMstatus_t;

typedef union CsrSatp_ {
  struct {
#ifdef CONFIG_RV64
    word_t ppn : 44;
    word_t asid : 16;
    word_t mode : 4;
#else
    word_t ppn : 22;
    word_t asid : 9;
    word_t mode : 1;
#endif
  };
  word_t packed;
} CsrSatp_t;

#define gpr(idx) (cpu.gpr[check_gpr_idx(idx)])
#define csr(idx) (cpu.csr[check_csr_reg(idx)])

static inline const char *reg_name(int idx) {
  extern const char *regs[];
  return regs[check_gpr_idx(idx)];
}

#endif
