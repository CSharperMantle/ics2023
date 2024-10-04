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
  int priv;
  bool intr;
} MUXDEF(CONFIG_RV64, riscv64_CPU_state, riscv32_CPU_state);

#define MVENDORID 0x79737978
#define MARCHID   0x015fdf40
#define MIMPID    0x00000001

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

enum {
  MEM_PAGING_BARE = 0,
#ifdef CONFIG_RV64
  MEM_PAGING_SV39 = 8,
  MEM_PAGING_SV48 = 9,
#else
  MEM_PAGING_SV32 = 1,
#endif
};

enum {
    PRIV_MODE_U = 0,
    PRIV_MODE_S = 1,
    PRIV_MODE_M = 3,
};

typedef union Pte_ {
  struct {
    word_t v : 1;
    word_t r : 1;
    word_t w : 1;
    word_t x : 1;
    word_t u : 1;
    word_t g : 1;
    word_t a : 1;
    word_t d : 1;
    word_t rsw : 2;
#ifdef CONFIG_RV64
    word_t ppn0 : 9;
    word_t ppn1 : 9;
    word_t ppn2 : 26;
    word_t resv0_ : 7;
    word_t pbmt : 2;
    word_t n : 1;
#else
    word_t ppn0 : 10;
    word_t ppn1 : 12;
#endif
  };
  struct {
    word_t flags : 8;
    word_t : 2;
#ifdef CONFIG_RV64
    word_t ppn : 44;
#else
    word_t ppn : 22;
#endif
  };
  word_t packed;
} Pte_t;

#endif
