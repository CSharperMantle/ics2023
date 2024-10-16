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

#include "local-include/reg.h"
#include <isa.h>

const char *regs[] = {"$0", "ra", "sp", "gp", "tp",  "t0",  "t1", "t2", "s0", "s1", "a0",
                      "a1", "a2", "a3", "a4", "a5",  "a6",  "a7", "s2", "s3", "s4", "s5",
                      "s6", "s7", "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"};

#define FMT_INT_LADJ_W_ MUXDEF(CONFIG_ISA64, "%-*" PRId64, "%-*" PRId32)

void isa_reg_display() {
  for (int i = 0; i < ARRLEN(cpu.gpr); i++) {
    printf("%-*s " FMT_WORD " " FMT_INT_LADJ_W_ "%c",
           8,
           reg_name(i),
           gpr(i),
           24,
           gpr(i),
           i % 2 == 0 ? ' ' : '\n');
  }
  printf("%-*s " FMT_WORD "\n", 8, "pc", cpu.pc);
}

word_t isa_reg_str2val(const char *s, bool *success) {
  for (int i = 0; i < ARRLEN(regs); i++) {
    if (strcmp(reg_name(i), s) == 0) {
      if (success != NULL) {
        *success = true;
      }
      return gpr(i);
    }
  }
  if (strcmp(s, "pc") == 0) {
    if (success != NULL) {
      *success = true;
    }
    return cpu.pc;
  }
  *success = false;
  return 0;
}
