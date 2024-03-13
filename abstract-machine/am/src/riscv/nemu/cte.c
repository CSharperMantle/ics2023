#include <am.h>
#include <klib.h>
#include <riscv/riscv.h>

#define BITMASK(bits)   ((1ull << (bits)) - 1)
#define BITS(x, hi, lo) (((x) >> (lo)) & BITMASK((hi) - (lo) + 1))

static Context *(*user_handler)(Event, Context *) = NULL;

Context *__am_irq_handle(Context *c) {
  if (user_handler) {
    Event ev;

    if (!BITS(c->mcause, sizeof(c->mcause) * 8 - 1, sizeof(c->mcause) * 8 - 1)) {
      ev = (Event){.event = EVENT_SYSCALL};
    } else if (c->mcause == -1) {
      ev = (Event){.event = EVENT_YIELD};
    } else {
      ev = (Event){.event = EVENT_ERROR};
    }

    c = user_handler(ev, c);
    assert(c != NULL);
  }

  // TODO: distinguish int vs fault
  c->mepc += 4;

  return c;
}

extern void __am_asm_trap(void);

bool cte_init(Context *(*handler)(Event, Context *)) {
  // initialize exception entry
  asm volatile("csrw mtvec, %0" : : "r"(__am_asm_trap));

  // register event handler
  user_handler = handler;

  return true;
}

Context *kcontext(Area kstack, void (*entry)(void *), void *arg) {
  return NULL;
}

void yield() {
#ifdef __riscv_e
  asm volatile("li a5, -1; ecall");
#else
  asm volatile("li a7, -1; ecall");
#endif
}

bool ienabled() {
  return false;
}

void iset(bool enable) {}
