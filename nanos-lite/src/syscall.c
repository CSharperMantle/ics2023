#include "syscall.h"
#include <common.h>

#ifdef CONFIG_STRACE
static void print_strace(uintptr_t *a) {
  Log("syscall %u; args=[0x%p, 0x%p, 0x%p]", a[0], a[1], a[2], a[3]);
}
#endif

static int syscall_write(int fd, void *buf, size_t count) {
  if (!(fd == 1 || fd == 2)) {
    return -1;
  }
  for (size_t i = 0; i < count; i++) {
    putchar(((char *)buf)[i]);
  }
  return count;
}

static int syscall_brk(void *ptr) {
  (void)ptr; // TODO: single task OS; always succeed
  return 0;
}

void do_syscall(Context *c) {
  uintptr_t a[4];
  a[0] = c->GPR1;
  a[1] = c->GPR2;
  a[2] = c->GPR3;
  a[3] = c->GPR4;

#ifdef CONFIG_STRACE
  print_strace(a);
#endif

  switch (a[0]) {
    case SYS_yield:
      yield();
      c->GPRx = 0;
      break;
    case SYS_exit:
      halt(a[1]);
      c->GPRx = 0;
      break;
    case SYS_write: c->GPRx = syscall_write((int)a[1], (void *)a[2], (size_t)a[3]); break;
    case SYS_brk: c->GPRx = (uintptr_t)syscall_brk((void *)a[0]); break;
    default: panic("Unhandled syscall ID = %d", a[0]);
  }
}
