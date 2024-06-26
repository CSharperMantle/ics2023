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

#ifndef __MEMORY_PADDR_H__
#define __MEMORY_PADDR_H__

#include <common.h>

#define PMEM_LEFT    ((paddr_t)CONFIG_MBASE)
#define PMEM_RIGHT   ((paddr_t)CONFIG_MBASE + CONFIG_MSIZE - 1)
#define RESET_VECTOR (PMEM_LEFT + CONFIG_PC_RESET_OFFSET)

#if defined(CONFIG_PMEM_MALLOC)
extern uint8_t *pmem;
#else // CONFIG_PMEM_GARRAY
extern uint8_t pmem[CONFIG_MSIZE] PG_ALIGN;
#endif

typedef union Paddr_ {
  struct {
    word_t offset : 12;
#ifdef CONFIG_RV64
    word_t ppn0 : 9;
    word_t ppn1 : 9;
    word_t ppn2 : 26;
#else
    word_t ppn0 : 10;
    word_t ppn1 : 10; // FIXME: 12
#endif
  };
  struct {
    word_t : 12;
#ifdef CONFIG_RV64
    word_t ppn : 44;
#else
    word_t ppn : 20; // FIXME: 22
#endif
  };
  word_t packed;
} Paddr_t;

/* convert the guest physical address in the guest program to host virtual address in NEMU */
uint8_t *guest_to_host(paddr_t paddr);
/* convert the host virtual address in NEMU to guest physical address in the guest program */
paddr_t host_to_guest(uint8_t *haddr);

static inline bool in_pmem(paddr_t addr) {
  return addr - CONFIG_MBASE < CONFIG_MSIZE;
}

word_t paddr_read(paddr_t addr, int len);
void paddr_write(paddr_t addr, int len, word_t data);

#endif
