#include <loader.h>
#include <proc.h>

#define MAX_NR_PROC 4

PCB pcb[MAX_NR_PROC] = {};
static PCB pcb_boot __attribute__((used)) = {};
PCB *current = NULL;

void switch_boot_pcb(void) {
  current = &pcb_boot;
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
  context_kload(&pcb[1], hello_fun, (void *)1);
  context_uload(&pcb[0], "/bin/pal", NULL, NULL);
  switch_boot_pcb();
}

Context *schedule(Context *ctx) {
  current->cp = ctx;
  current = (current == &pcb[0] ? &pcb[1] : &pcb[0]);
  //Log("context: %p; kernel stack: %p", current->cp, current->cp->mscratch);
  return current->cp;
}
