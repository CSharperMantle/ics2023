#ifndef SDB_INTEROP_H_INCLUDED_
#define SDB_INTEROP_H_INCLUDED_

#include <common.h>

#ifdef CONFIG_WATCHPOINT
void eval_watchpoints(void);
#endif

#ifdef CONFIG_FTRACE
// void ftrace_reset(void);
void ftrace_queue_call(vaddr_t pc, vaddr_t target);
void ftrace_queue_ret(vaddr_t pc, vaddr_t target);
#endif

#endif /* SDB_INTEROP_H_INCLUDED_ */
