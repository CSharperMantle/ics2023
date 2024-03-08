#include "../monitor/sdb/sdb.h"
#include <cpu/cpu.h>
#include <cpu/sdb_interop.h>

void eval_watchpoints(void) {
#ifdef CONFIG_WATCHPOINT
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
      Log("watchpoint %d: cannot eval", p->NO);
    }
    p = p->next;
  }
#endif
}
