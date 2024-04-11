#ifdef NDEBUG
#undef NDEBUG
#endif

#include "Vlight.h"
#include "verilated.h"
#include <cassert>
#include <cstdlib>
#include <iostream>
#include <nvboard.h>

typedef Vlight Dut;

extern void nvboard_bind_all_pins(Dut *top);

static void single_cycle(Dut &dut) {
  dut.clk = 0;
  dut.eval();
  nvboard_update();
  dut.clk = 1;
  dut.eval();
  nvboard_update();
}

static void reset(Dut &dut, int n) {
  dut.rst = 1;
  while (n-- > 0) {
    single_cycle(dut);
  }
  dut.rst = 0;
}

int main(int argc, char *argv[]) {
  VerilatedContext ctx{};
  ctx.commandArgs(argc, argv);

  Dut dut{&ctx};
  nvboard_bind_all_pins(&dut);
  nvboard_init();

  reset(dut, 10);
  while (!ctx.gotFinish()) {
    single_cycle(dut);
  }

  nvboard_quit();

  return 0;
}
