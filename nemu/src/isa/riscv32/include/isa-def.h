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

#ifndef __ISA_RISCV_H__
#define __ISA_RISCV_H__

#include "../local-include/reg.h"
#include <common.h>

typedef struct {
  word_t gpr[MUXDEF(CONFIG_RVE, 16, 32)];
  word_t csr[4096];
  vaddr_t pc;
} MUXDEF(CONFIG_RV64, riscv64_CPU_state, riscv32_CPU_state);

// decode
typedef struct {
  union {
    uint32_t val;
  } inst;
#ifdef CONFIG_FTRACE
  uint8_t is_jal : 1;
  uint8_t is_jalr : 1;
  vaddr_t target;
#endif
} MUXDEF(CONFIG_RV64, riscv64_ISADecodeInfo, riscv32_ISADecodeInfo);

#define isa_mmu_check(vaddr, len, type) (MMU_DIRECT)

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
