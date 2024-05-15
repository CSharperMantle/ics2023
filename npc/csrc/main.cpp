#include "VTop.h"
#include <cstdint>
#include <nvboard.h>
#include <verilated.h>
#include <verilated_vcd_c.h>

#define DUMP_WAVE 0

extern void nvboard_bind_all_pins(VTop *);

static VerilatedContext *ctx = nullptr;
static VerilatedVcdC *tf = nullptr;
static VTop dut{};

static void step_and_dump_wave() {
  dut.eval();
  ctx->timeInc(1);
#if defined(DUMP_WAVE) && DUMP_WAVE
  tf->dump(ctx->time());
#endif
}

static void cycle() {
  dut.clock = 0;
  step_and_dump_wave();
  dut.clock = 1;
  step_and_dump_wave();
}

static void sim_init() {
  ctx = Verilated::defaultContextp();
  tf = new VerilatedVcdC();
#if defined(DUMP_WAVE) && DUMP_WAVE
  ctx->traceEverOn(true);
  dut.trace(tf, 0);
  tf->open("dump.vcd");
#endif
}

static void sim_exit() {
  step_and_dump_wave();
#if defined(DUMP_WAVE) && DUMP_WAVE
  tf->close();
#endif
}

int main() {
  sim_init();
  nvboard_bind_all_pins(&dut);
  nvboard_init();

  dut.reset = 1;
  cycle();
  dut.reset = 0;

  while (true) {
    nvboard_update();
    cycle();
  }

  nvboard_quit();
  sim_exit();
}