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

#include "../local-include/reg.h"
#include <cpu/difftest.h>
#include <isa.h>

static void report_difftest_err(const char *name, vaddr_t pc, word_t expected, word_t actual) {
  Error(FMT_WORD ": DIFF: %s, expected " FMT_WORD ", actual " FMT_WORD, pc, name, expected, actual);
}

bool isa_difftest_checkregs(CPU_state *ref_r, vaddr_t pc) {
  bool equiv = true;
  for (size_t i = 0; i < ARRLEN(cpu.gpr); i++) {
    if (ref_r->gpr[i] != cpu.gpr[i]) {
      report_difftest_err(reg_name(i), pc, ref_r->gpr[i], cpu.gpr[i]);
      equiv = false;
    }
  }
  return equiv;
}

void isa_difftest_attach() {}
