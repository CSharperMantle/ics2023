#include "syscall.h"
#include <common.h>
#include <errno.h>
#include <fcntl.h>
#include <fs.h>
#include <loader.h>
#include <memory.h>
#include <proc.h>
#include <sys/time.h>

#ifdef CONFIG_STRACE
static void print_strace(uintptr_t *a) {
  Log("syscall %u; args=[%p, %p, %p]", a[0], a[1], a[2], a[3]);
}
#endif

typedef struct timeval tv_t;
typedef struct timezone tz_t;

static int syscall_execve(const char *filename, char *const argv[], char *const envp[]) {
  (void)argv;
  (void)envp;

  int f = fs_open(filename, O_RDONLY, 0);
  if (f < 0) {
    return -ENOENT;
  }
  fs_close(f);

  Log("switching to \"%s\"", filename);
  context_uload(current, filename, argv, envp);
  switch_boot_pcb();
  yield();

  assert(((void)"unreachable", 0));
}

static int syscall_gettimeofday(tv_t *tv, tz_t *tz) {
  AM_TIMER_UPTIME_T state = io_read(AM_TIMER_UPTIME);
  if (tv != NULL) {
    tv->tv_sec = state.us / 1000000;
    tv->tv_usec = state.us % 1000000;
  }
  // TODO: set timezone
  if (tz != NULL) {
    assert(((void)"timezone not implemented", 0));
  }
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
    case SYS_exit: c->GPRx = syscall_execve("/bin/menu", NULL, NULL); break; /* noreturn */
    case SYS_yield:
      yield();
      c->GPRx = 0;
      break;
    case SYS_open: c->GPRx = fs_open((const char *)a[1], (int)a[2], (int)a[3]); break;
    case SYS_read: c->GPRx = fs_read((int)a[1], (void *)a[2], (size_t)a[3]); break;
    case SYS_write: c->GPRx = fs_write((int)a[1], (const void *)a[2], (size_t)a[3]); break;
    case SYS_close: c->GPRx = fs_close((int)a[1]); break;
    case SYS_lseek: c->GPRx = fs_lseek((int)a[1], (off_t)a[2], (int)a[3]); break;
    case SYS_brk: c->GPRx = mm_brk(a[1]); break;
    case SYS_execve:
      c->GPRx = syscall_execve((const char *)a[1], (char **)a[2], (char **)a[3]);
      break;
    case SYS_gettimeofday: c->GPRx = syscall_gettimeofday((tv_t *)a[1], (tz_t *)a[2]); break;
    default: panic("Unhandled syscall ID = %d", a[0]);
  }
}
