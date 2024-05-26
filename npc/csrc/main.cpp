#include "VTop.h"
#include <cstdint>
#include <verilated.h>
#include <verilated_vcd_c.h>

#define DUMP_WAVE 1

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

static uint32_t ram_initial[] = {
    0x00000297, // auipc t0,0
    0x00028823, // sb  zero,16(t0)
    0x0102c503, // lbu a0,16(t0)
    0x00100073, // ebreak (used as nemu_trap)
    0xdeadbeef, // some data
};

extern "C" void npc_dpi_ifu(int pc, int *instr) {
  const uint32_t upc = static_cast<uint32_t>(pc);
  printf("PC=0x%08x\n", (uint32_t)pc);
  if (upc > 0x80000000 && upc < 0x80000000 + sizeof(ram_initial)) {
    *instr = ram_initial[(upc - 0x80000000) / 4];
  } else {
    *instr = 0;
  }
}

extern "C" void npc_dpi_memu(char mem_width,
                             svBit mem_u,
                             svBit mem_r_en,
                             int mem_r_addr,
                             int *mem_r_data,
                             svBit mem_w_en,
                             int mem_w_addr,
                             int mem_w_data) {}

int main(int argc, char *argv[]) {
  sim_init();

  dut.reset = 1;
  cycle();
  dut.reset = 0;

  do {
    cycle();
  } while (!dut.io_break);

  printf("Exit with 0x%08x\n", (uint32_t)dut.io_ra);

  sim_exit();

  return 0;
}