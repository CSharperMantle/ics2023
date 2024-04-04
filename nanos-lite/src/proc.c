#include <loader.h>
#include <proc.h>

static PCB procs[MAX_NR_PROC] = {0};
static PCB proc_boot __attribute__((used)) = {0};
static PCB *proc_fg = NULL;
static size_t proc_fg_slices = 0;
static PCB *proc_bg = NULL;
PCB *current = NULL;

void switch_boot_pcb(void) {
  current = &proc_boot;
}

__attribute__((noreturn)) void hello_fun(void *arg) {
  int j = 1;
  while (1) {
    Log("Hello World from Nanos-lite with arg '%p' for the %dth time!", (uintptr_t)arg, j);
    j++;
    yield();
  }
}

void init_proc(void) {
  static char *const EMPTY[] __attribute__((used)) = {NULL};

  Log("Initializing processes...");
  // context_kload(&pcb[0], hello_fun, (void *)1);
  // context_kload(&pcb[1], hello_fun, (void *)2);
  context_uload(&procs[0], "/bin/hello", NULL, NULL);
  context_uload(&procs[1], "/bin/nterm", NULL, NULL);
  context_uload(&procs[2], "/bin/nslider", NULL, NULL);
  context_uload(&procs[3], "/bin/bird", NULL, NULL);
  proc_fg = &procs[1];
  proc_bg = &procs[0];
  switch_boot_pcb();
}

Context *schedule(Context *ctx) {
  current->cp = ctx;

  if (current == proc_fg) {
    if (proc_fg_slices + 1 > SCHED_FG_SLICES) {
      current = proc_bg;
#ifdef CONFIG_SCHEDTRACE
      Log("%p (p=%d, a0=%p, sp=%p) -> %p (p=%d, a0=%p, sp=%p)",
          ctx,
          (int)ctx->np,
          (void *)ctx->GPRx,
          (void *)ctx->gpr[2],
          current->cp,
          (int)current->cp->np,
          (void *)current->cp->GPRx,
          (void *)current->cp->gpr[2]);
#endif
    } else {
#ifdef CONFIG_SCHEDTRACE
      Log("fg has run for %d slices", (int)proc_fg_slices);
#endif
      proc_fg_slices++;
    }
  } else {
    current = proc_fg;
    proc_fg_slices = 1;
#ifdef CONFIG_SCHEDTRACE
    Log("%p (p=%d, a0=%p, sp=%p) -> %p (p=%d, a0=%p, sp=%p)",
        ctx,
        (int)ctx->np,
        (void *)ctx->GPRx,
        (void *)ctx->gpr[2],
        current->cp,
        (int)current->cp->np,
        (void *)current->cp->GPRx,
        (void *)current->cp->gpr[2]);
#endif
  }

  return current->cp;
}

void change_proc_fg(int idx) {
  assert(idx >= 1 && idx <= MAX_NR_PROC - 1);

  proc_fg = &procs[idx];
}