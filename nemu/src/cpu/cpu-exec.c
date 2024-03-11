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

#include "common.h"
#include "isa.h"
#include "macro.h"
#include <cpu/cpu.h>
#include <cpu/decode.h>
#include <cpu/difftest.h>
#include <cpu/sdb_interop.h>
#ifdef CONFIG_IRINGBUF
#include <cpu/iringbuf.h>
#endif
#include <locale.h>

/* The assembly code of instructions executed is only output to the screen
 * when the number of instructions executed is less than this value.
 * This is useful when you use the `si' command.
 * You can modify this value as you want.
 */
#define MAX_INST_TO_PRINT 10

CPU_state cpu = {};
uint64_t g_nr_guest_inst = 0;
static uint64_t g_timer = 0; // unit: us

void device_update(void);

#if defined(CONFIG_TRACE) || defined(CONFIG_DIFFTEST)
#ifdef CONFIG_ITRACE
static bool g_print_step = false;
#endif

static void trace_and_difftest(Decode *_this, vaddr_t dnpc) {
#ifdef CONFIG_ITRACE
  log_write("%s\n", _this->logbuf);
  if (g_print_step) {
    puts(_this->logbuf);
  }
#endif
  IFDEF(CONFIG_DIFFTEST, difftest_step(_this->pc, dnpc));
  IFDEF(CONFIG_WATCHPOINT, eval_watchpoints());
#ifdef CONFIG_FTRACE
  assert(likely(!(_this->isa.is_jal && _this->isa.is_jalr)));
  if (_this->isa.is_jal) {
    ftrace_queue_jal(_this->pc, _this->dnpc);
  }
  if (_this->isa.is_jalr) {
    ftrace_queue_jalr(_this->pc, _this->dnpc);
  }
#endif
}

#if defined(CONFIG_ITRACE) || defined(CONFIG_IRINGBUF)
#ifndef CONFIG_ISA_loongarch32r
void disasm(char *str, int size, uint64_t pc, uint8_t *code, int nbyte);
#endif

static void disasm_into_buf(char *buf, int buf_len, vaddr_t pc, vaddr_t snpc, uint8_t *inst) {
  char *p = buf;
  p += snprintf(p, buf_len, FMT_WORD ":", pc);
  int inst_len = snpc - pc;
  for (int i = inst_len - 1; i >= 0; i--) {
    p += snprintf(p, 4, " %02x", inst[i]);
  }
  int ilen_max = MUXDEF(CONFIG_ISA_x86, 8, 4);
  int space_len = ilen_max - inst_len;
  if (space_len < 0) {
    space_len = 0;
  }
  space_len = space_len * 3 + 1;
  memset(p, ' ', space_len);
  p += space_len;

#ifndef CONFIG_ISA_loongarch32r
  disasm(p, buf + buf_len - p, MUXDEF(CONFIG_ISA_x86, snpc, pc), inst, inst_len);
#else
  buf[0] = '\0'; // the upstream llvm does not support loongarch32r
#endif
}
#endif
#endif

static void exec_once(Decode *s, vaddr_t pc) {
  s->pc = pc;
  s->snpc = pc;
  isa_exec_once(s);
  cpu.pc = s->dnpc;
#ifdef CONFIG_ITRACE
  disasm_into_buf(s->logbuf, sizeof(s->logbuf), s->pc, s->snpc, (uint8_t *)&s->isa.inst.val);
#endif
}

static void execute(uint64_t n) {
  Decode s;
  for (; n > 0; n--) {
    exec_once(&s, cpu.pc);
    g_nr_guest_inst++;
    IFDEF(CONFIG_IRINGBUF, iringbuf_insert(s.pc, s.isa.inst.val));
#if defined(CONFIG_TRACE) || defined(CONFIG_DIFFTEST)
    trace_and_difftest(&s, cpu.pc);
#endif
    if (nemu_state.state != NEMU_RUNNING)
      break;
    IFDEF(CONFIG_DEVICE, device_update());
  }
}

static void statistic(void) {
  IFNDEF(CONFIG_TARGET_AM, setlocale(LC_NUMERIC, ""));
#define NUMBERIC_FMT MUXDEF(CONFIG_TARGET_AM, "%", "%'") PRIu64
  Log("host time spent = " NUMBERIC_FMT " us", g_timer);
  Log("total guest instructions = " NUMBERIC_FMT, g_nr_guest_inst);
  if (g_timer > 0)
    Log("simulation frequency = " NUMBERIC_FMT " inst/s", g_nr_guest_inst * 1000000 / g_timer);
  else
    Log("Finish running in less than 1 us and can not calculate the simulation frequency");
}

void assert_fail_msg() {
  isa_reg_display();
  statistic();
}

#ifdef CONFIG_IRINGBUF
static void print_iringbuf(void) {
  Log("- - - %d recent instructions (top: oldest)", IRINGBUF_NR_ELEM);
  size_t id_inst = iringbuf.tail;
  do {
    vaddr_t addr = iringbuf.insts[id_inst].addr;
    uint32_t val = iringbuf.insts[id_inst].inst_val;
#ifndef CONFIG_ISA_loongarch32r
    static char buf[128];
    buf[0] = '\0';
    disasm_into_buf(buf, sizeof(buf), addr, addr + sizeof(val), (uint8_t *)&val);
#endif
    Log("%s", MUXNDEF(CONFIG_ISA_loongarch32r, buf, " Disasm N/A"));
    id_inst = (id_inst + 1) % IRINGBUF_NR_ELEM;
  } while (id_inst != iringbuf.head);
  Log("- - - %d recent instructions (bottom: newest)", IRINGBUF_NR_ELEM);
}
#endif

/* Simulate how the CPU works. */
void cpu_exec(uint64_t n) {
#ifdef CONFIG_ITRACE
  g_print_step = (n < MAX_INST_TO_PRINT);
#endif
  switch (nemu_state.state) {
    case NEMU_END:
    case NEMU_ABORT:
      printf("Program execution has ended. To restart the program, exit NEMU and run again.\n");
      return;
    default: nemu_state.state = NEMU_RUNNING; break;
  }

  const uint64_t timer_start = get_time();

  execute(n);

  const uint64_t timer_end = get_time();
  g_timer += timer_end - timer_start;

  switch (nemu_state.state) {
    case NEMU_RUNNING: nemu_state.state = NEMU_STOP; break;
    case NEMU_END:
      Log("nemu: %s at pc = " FMT_WORD,
          nemu_state.halt_ret == 0 ? ANSI_FMT("HIT GOOD TRAP", ANSI_FG_GREEN)
                                   : ANSI_FMT("HIT BAD TRAP", ANSI_FG_RED),
          nemu_state.halt_pc);
#ifdef CONFIG_IRINGBUF
      if (nemu_state.halt_ret != 0) {
        print_iringbuf();
      }
#endif
      statistic();
      break;
    case NEMU_ABORT:
      Log("nemu: %s at pc = " FMT_WORD, ANSI_FMT("ABORT", ANSI_FG_RED), nemu_state.halt_pc);
      IFDEF(CONFIG_IRINGBUF, print_iringbuf());
      statistic();
      break;
    case NEMU_QUIT: statistic(); break;
  }
}
