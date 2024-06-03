
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <verilated.h>
#include <verilated_vcd_c.h>

#include "VTop.h"
#include "VTop__Syms.h"
#include "common.hpp"
#include "debug.hpp"
#include "difftest.hpp"
#include "mem/host.hpp"

#define DUMP_WAVE 1

static const uint32_t DEFAULT_IMG[] = {
    0x00000297, // auipc t0,0
    0x00028823, // sb  zero,16(t0)
    0x0102c503, // lbu a0,16(t0)
    0x00100073, // ebreak (used as nemu_trap)
    0xdeadbeef, // some data
};

VTop dut{};
static CpuState cpu_state_dut{};
static CpuState cpu_state_ref{};

static VerilatedContext *ctx = nullptr;
static VerilatedVcdC *tf = nullptr;

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
  memcpy(cpu_state_dut.gpr,
         dut.rootp->Top__DOT__core__DOT__gpr__DOT__regs_ext__DOT__Memory.data(),
         sizeof(cpu_state_dut.gpr));
  cpu_state_dut.pc = dut.io_pc;
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

int main(int argc, char *argv[]) {
  size_t len_img = 0;
  if (argc < 2) {
    Warn("no image file provided, fallback to builtin image");
    std::memcpy(host_mem, DEFAULT_IMG, sizeof(DEFAULT_IMG));
    len_img = sizeof(DEFAULT_IMG);
  } else {
    FILE *const f_img = std::fopen(argv[1], "rb");
    Assert(f_img, "cannot open \"%s\": errno %d: %s", argv[1], errno, std::strerror(errno));
    std::fseek(f_img, 0, SEEK_END);
    len_img = static_cast<size_t>(std::ftell(f_img));
    Log("image \"%s\", size=%zu", argv[1], len_img);
    fseek(f_img, 0, SEEK_SET);
    const size_t nbytes_read = fread(guest_to_host(RESET_VECTOR), 1, len_img, f_img);
    Assert(nbytes_read == len_img, "cannot read %zu bytes, %zu already read", len_img, nbytes_read);
  }

  const char *env_ref_so = getenv("NPC_DIFFTEST_REF_SO");
  if (env_ref_so == nullptr || std::strlen(env_ref_so) == 0) {
    Log("difftest: not initialized; env var NPC_DIFFTEST_REF_SO=%s",
        env_ref_so == nullptr ? "(not found)" : env_ref_so);
    load_difftest(nullptr, len_img, cpu_state_dut);
  } else {
    Log("difftest: loading ref so \"%s\"", env_ref_so);
    load_difftest(env_ref_so, len_img, cpu_state_dut);
  }

  sim_init();
  dut.reset = 1;
  cycle();
  dut.reset = 0;

  do {
    ref_difftest_regcpy(&cpu_state_dut, DiffTestCopyDir::ToRef);
    cycle();
    ref_difftest_exec(1);
    ref_difftest_regcpy(&cpu_state_ref, DiffTestCopyDir::ToDut);
    difftest_check(cpu_state_ref, cpu_state_dut);
  } while (!dut.io_break);

  const word_t retval = dut.io_a0;
  if (retval == 0) {
    Log("npc: " ANSI_FMT("HIT GOOD TRAP", ANSI_FG_GREEN) " at pc = " FMT_WORD,
        static_cast<word_t>(dut.io_pc));
  } else {
    Log("npc: " ANSI_FMT("HIT BAD TRAP", ANSI_FG_RED) " at pc = " FMT_WORD,
        static_cast<word_t>(dut.io_pc));
  }

  sim_exit();
  return (int)retval;
}