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

#ifndef __SDB_H__
#define __SDB_H__

#include <common.h>

word_t expr(char *e, bool *success);

#ifdef CONFIG_FTRACE
#include <elf.h>

#define FTRACE_PRINT_MAX_DEPTH      64
#define FTRACE_PRINT_WRAP_THRESHOLD 32

typedef struct SdbElfFunc_ {
  char *name;
  Elf64_Addr start;
  Elf64_Addr end;
} SdbElfFunc_t;

typedef struct SdbElf_ {
  Elf64_Ehdr ehdr;
  Elf64_Sym *symtab;
  size_t nent_symtab;
  char *strtab;
  SdbElfFunc_t *funcs;
  size_t nent_funcs;
  bool valid;
} SdbElf_t;

extern SdbElf_t elf;
extern int64_t ftrace_call_level;

int elf_read(FILE *felf);
char *elf_get_func_name(uint64_t addr, bool *at_start);
#endif

#ifdef CONFIG_WATCHPOINT
#define NR_WP           32
#define WP_MAX_EXPR_LEN 255

typedef struct watchpoint {
  int NO;
  struct watchpoint *next;
  word_t prev_state;
  char expr[WP_MAX_EXPR_LEN + 1];
} WP;

WP *watchpoint_head(void);
void watchpoint_print_all(void);
void watchpoint_print_at(int no);
void watchpoint_add(const char *e);
void watchpoint_delete(int no);
#endif

#endif
