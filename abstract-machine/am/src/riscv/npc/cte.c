#include <am.h>
#include <klib.h>
#include <riscv/riscv.h>

static Context *(*user_handler)(Event, Context *) = NULL;

Context *__am_irq_handle(Context *c) {
  CsrMcause_t mcause;

  if (user_handler) {
    Event ev = {0};

    mcause.packed = c->mcause;
    if (mcause.intr) {
      // Interrupt handlers
      switch (mcause.code) {
        case INTR_M_TIMR: ev.event = EVENT_IRQ_TIMER; break;
        default: ev.event = EVENT_ERROR; break;
      }
    } else {
      // Exception handlers
      switch (mcause.code) {
        case EXCP_U_ENV_CALL:
        case EXCP_M_ENV_CALL:
          switch (c->GPR1) {
            case -1: ev.event = EVENT_YIELD; break;
            default: ev.event = EVENT_SYSCALL; break;
          }
          break;
        default: ev.event = EVENT_ERROR; break;
      }
    }

    c = user_handler(ev, c);
    assert(c != NULL);
  }

  mcause.packed = c->mcause;
  if (!mcause.intr) {
    c->mepc += 4;
  }

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
