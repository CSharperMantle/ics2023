#include "../monitor/sdb/sdb.h"
#include <cpu/cpu.h>
#include <cpu/sdb_interop.h>
#include <string.h>

#ifdef CONFIG_WATCHPOINT
void eval_watchpoints(void) {
  WP *p = watchpoint_head();
  while (p != NULL) {
    bool success = false;
    word_t state = expr(p->expr, &success);
    if (success) {
      if (state != p->prev_state) {
        Log("watchpoint %d: state change; " FMT_WORD " -> " FMT_WORD, p->NO, p->prev_state, state);
        printf("Watchpoint %d hit\n", p->NO);
        nemu_state.state = NEMU_STOP;
        p->prev_state = state;
      }
    } else {
      Warn("watchpoint %d: cannot eval", p->NO);
    }
    p = p->next;
  }
}
#endif

#ifdef CONFIG_FTRACE
#define MIN_(a_, b_)            ((a_) <= (b_) ? (a_) : (b_))
#define MAX_(a_, b_)            ((a_) >= (b_) ? (a_) : (b_))
#define WRAP_ABOVE_(v_, t_, m_) ((t_) + ((v_) - (t_)) % ((m_) - (t_)))

static int get_n_spaces(int64_t level) {
  level = MAX_(level, 0);
  level = level < FTRACE_PRINT_WRAP_THRESHOLD
              ? level
              : WRAP_ABOVE_(level, FTRACE_PRINT_WRAP_THRESHOLD, FTRACE_PRINT_MAX_DEPTH);
  return (int)level + 1;
}

static void print_ftrace(bool call, vaddr_t pc, vaddr_t target, const char *name) {
  if (call) {
    Log("%04ld %*ccall %s@" FMT_WORD,
        ftrace_call_level,
        get_n_spaces(ftrace_call_level),
        ' ',
        name,
        target);
    ftrace_call_level += 1;
  } else {
    ftrace_call_level -= 1;
    Log("%04ld %*cret -> %s", ftrace_call_level, get_n_spaces(ftrace_call_level), ' ', name);
  }
}

void ftrace_queue_jal(vaddr_t pc, vaddr_t target) {
  print_ftrace(true, pc, target, elf_get_func_name(target, NULL));
}

void ftrace_queue_jalr(vaddr_t pc, vaddr_t target) {
  bool at_start;
  const char *const name = elf_get_func_name(target, &at_start);
  if (at_start) {
    print_ftrace(true, pc, target, name);
  } else {
    print_ftrace(false, pc, target, name);
  }
}
#endif
