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

#include <device/mmio.h>
#include <isa.h>
#include <memory/host.h>
#include <memory/paddr.h>

#ifdef CONFIG_TARGET_SHARE
#if defined(CONFIG_PMEM_MALLOC)
uint8_t *sram = NULL;
uint8_t *mrom = NULL;
uint8_t *flash = NULL;
uint8_t *psram = NULL;
uint8_t *sdram = NULL;
#else
uint8_t sram[CONFIG_SRAM_SIZE] = {};
uint8_t mrom[CONFIG_MROM_SIZE] = {};
uint8_t flash[CONFIG_FLASH_SIZE] = {};
uint8_t psram[CONFIG_PSRAM_SIZE] = {};
uint8_t sdram[CONFIG_SDRAM_SIZE] = {};
#endif
#else
#if defined(CONFIG_PMEM_MALLOC)
uint8_t *pmem = NULL;
#else
uint8_t pmem[CONFIG_MSIZE] PG_ALIGN = {};
#endif
#endif

static word_t backed_read(const PmemArea_t *area, paddr_t addr, int len) {
  Assert(area->backed, "area is not backed");
  return host_read(area->ops.hbase + (addr - area->gbase), len);
}

static void backed_write(const PmemArea_t *area, paddr_t addr, int len, word_t data) {
  Assert(area->backed, "area is not backed");
  host_write(area->ops.hbase + (addr - area->gbase), len, data);
}

const PmemArea_t PMEM_AREAS[] = {
  // clang-format off
#ifdef CONFIG_TARGET_SHARE
    {"sram",  0x0f000000,   CONFIG_SRAM_SIZE,  true,  {.hbase = sram}                         },
    {"mrom",  0x20000000,   CONFIG_MROM_SIZE,  true,  {.hbase = mrom}                         },
    {"flash", 0x30000000,   CONFIG_FLASH_SIZE, true,  {.hbase = flash}                        },
    {"psram", 0x80000000,   CONFIG_PSRAM_SIZE, true,  {.hbase = psram}                        },
    {"sdram", 0xa0000000,   CONFIG_SDRAM_SIZE, true,  {.hbase = sdram}                        },
#else
    {"pmem",  CONFIG_MBASE, CONFIG_MSIZE,      true,  {.hbase = pmem}                         },
    {"mmio",  0xa0000000,   0x10000000,        false, {.read = mmio_read, .write = mmio_write}},
#endif
  // clang-format on
};

static const PmemArea_t *lookup_pmem(paddr_t addr, int len) {
  for (size_t i = 0; i < ARRLEN(PMEM_AREAS); i++) {
    if (addr >= PMEM_AREAS[i].gbase && addr + len - 1 < PMEM_AREAS[i].gbase + PMEM_AREAS[i].len) {
      return &PMEM_AREAS[i];
    }
  }
  return NULL;
}

static __attribute__((noreturn)) void out_of_bound(paddr_t addr, const PmemArea_t *area) {
  if (area != NULL) {
    panic("addr " FMT_PADDR " is out of bound at map [" FMT_PADDR ", " FMT_PADDR
          "] at pc=" FMT_WORD,
          addr,
          area->gbase,
          (paddr_t)(area->gbase + area->len),
          cpu.pc);
  } else {
    panic("addr " FMT_PADDR " is not mapped at pc=" FMT_WORD, addr, cpu.pc);
  }
}

#ifdef CONFIG_MTRACE
static void print_mtrace(paddr_t addr, int len, bool read, word_t data) {
  if (read) {
    Log("pc=" FMT_WORD ": mem: %-6s" FMT_PADDR "; len = %d", cpu.pc, "READ", addr, len);
  } else {
    Log("pc=" FMT_WORD ": mem: %-6s" FMT_PADDR "; len = %d; data=" FMT_WORD,
        cpu.pc,
        "WRITE",
        addr,
        len,
        data);
  }
}
#endif

uint8_t *guest_to_host(paddr_t paddr) {
  const PmemArea_t *area = lookup_pmem(paddr, 0);
  Assert(area != NULL, "addr " FMT_PADDR " is not mapped", paddr);
  Assert(area->backed,
         "addr " FMT_PADDR " in area \"%s\" is not backed by host memory",
         paddr,
         area->name);
  return area->ops.hbase + (paddr - area->gbase);
}

paddr_t host_to_guest(uint8_t *haddr) {
  for (size_t i = 0; i < ARRLEN(PMEM_AREAS); i++) {
    if (!PMEM_AREAS[i].backed) {
      continue;
    }
    if (haddr >= PMEM_AREAS[i].ops.hbase && haddr < PMEM_AREAS[i].ops.hbase + PMEM_AREAS[i].len) {
      return PMEM_AREAS[i].gbase + (haddr - PMEM_AREAS[i].ops.hbase);
    }
  }
  panic("no mapping found for %p", haddr);
}

void init_mem(void) {
#if defined(CONFIG_PMEM_MALLOC)
#ifdef CONFIG_TARGET_SHARE
  sram = malloc(CONFIG_SRAM_SIZE);
  assert(sram);
  mrom = malloc(CONFIG_MROM_SIZE);
  assert(mrom);
  flash = malloc(CONFIG_FLASH_SIZE);
  assert(flash);
  psram = malloc(CONFIG_PSRAM_SIZE);
  assert(psram);
  sdram = malloc(CONFIG_SDRAM_SIZE);
  assert(sdram);
#else
  pmem = malloc(CONFIG_MSIZE);
  assert(pmem);
  IFDEF(CONFIG_MEM_RANDOM, memset(pmem, rand(), CONFIG_MSIZE));
#endif
#endif
  for (size_t i = 0; i < ARRLEN(PMEM_AREAS); i++) {
    Log("Map memory area \"%s\" %c [" FMT_PADDR ", " FMT_PADDR "] @ %p",
        PMEM_AREAS[i].name,
        PMEM_AREAS[i].backed ? 'B' : 'U',
        PMEM_AREAS[i].gbase,
        (paddr_t)(PMEM_AREAS[i].gbase + PMEM_AREAS[i].len),
        PMEM_AREAS[i].ops.hbase);
  }
}

word_t paddr_read(paddr_t addr, int len) {
  IFDEF(CONFIG_MTRACE, print_mtrace(addr, len, true, 0));
  const PmemArea_t *const area = lookup_pmem(addr, len);
  if (unlikely(area == NULL)) {
    out_of_bound(addr, NULL);
  }
  if (likely(area->backed)) {
    return backed_read(area, addr, len);
  } else if (MUXDEF(CONFIG_DEVICE, true, false)) {
    return area->ops.read(addr, len);
  } else {
    out_of_bound(addr, area);
  }
}

void paddr_write(paddr_t addr, int len, word_t data) {
  IFDEF(CONFIG_MTRACE, print_mtrace(addr, len, false, data));
  const PmemArea_t *const area = lookup_pmem(addr, len);
  if (unlikely(area == NULL)) {
    out_of_bound(addr, area);
  }
  if (likely(area->backed)) {
    backed_write(area, addr, len, data);
  } else if (MUXDEF(CONFIG_DEVICE, true, false)) {
    area->ops.write(addr, len, data);
  } else {
    out_of_bound(addr, area);
  }
}
