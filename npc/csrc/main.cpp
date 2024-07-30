
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <verilated.h>
#include <verilated_vcd_c.h>

#include "VTop.h"
#include "VTop__Syms.h"
#include "common.hpp"
#include "debug.hpp"
#include "device/device.hpp"
#include "difftest.hpp"
#include "mem/host.hpp"
#include "util/disasm.hpp"
#include "util/iringbuf.hpp"

static const uint32_t DEFAULT_IMG[] = {
    0x00000297, // auipc t0,0
    0x00028823, // sb  zero,16(t0)
    0x0102c503, // lbu a0,16(t0)
    0x00100073, // ebreak (used as nemu_trap)
    0xdeadbeef, // some data
};

VTop dut{};

std::unique_ptr<DiffTest> difftest{};

static VerilatedContext *ctx = nullptr;
static VerilatedVcdC *tf = nullptr;

static void step_and_dump_wave() {
  dut.eval();
  ctx->timeInc(1);
#if defined(CONFIG_DUMP_WAVE) && CONFIG_DUMP_WAVE
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
#if defined(CONFIG_DUMP_WAVE) && CONFIG_DUMP_WAVE
  ctx->traceEverOn(true);
  dut.trace(tf, 0);
  tf->open("dump.vcd");
#endif
}

static void sim_exit() {
  step_and_dump_wave();
#if defined(CONFIG_DUMP_WAVE) && CONFIG_DUMP_WAVE
  tf->close();
#endif
}

static void print_iringbuf() {
  Log("- - - %d recent instructions (top: oldest)", CONFIG_IRINGBUF_NR_ELEM);
  for (const auto &instr : iringbuf) {
    auto instr_disasm = disasm(std::get<0>(instr),
                               reinterpret_cast<const uint8_t *>(&std::get<1>(instr)),
                               sizeof(std::get<1>(instr)));
    Log(FMT_WORD "\t%hhu\t%s", std::get<0>(instr), std::get<2>(instr), instr_disasm.c_str());
  }
  Log("- - - %d recent instructions (bottom: newest)", CONFIG_IRINGBUF_NR_ELEM);
}

void assert_fail_msg() {
  print_iringbuf();
#if defined(CONFIG_DUMP_WAVE) && CONFIG_DUMP_WAVE
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
    Log("difftest: not initialized; NPC_DIFFTEST_REF_SO=%s",
        env_ref_so == nullptr ? "(not found)" : env_ref_so);
    difftest = std::make_unique<DiffTest>(nullptr, len_img);
  } else {
    Log("difftest: loading ref so \"%s\"", env_ref_so);
    difftest = std::make_unique<DiffTest>(env_ref_so, len_img);
  }

#ifdef CONFIG_DEVICE
  init_device();
#endif

  init_disasm("riscv32-pc-linux-gnu");

  sim_init();
  dut.reset = 1;
  cycle();
  dut.reset = 0;
  difftest->sync_dut(dut);
  difftest->cycle_preamble();

  static word_t last_pc = 0;
  static size_t last_pc_count = 0;

  do {
    difftest->cycle();
    difftest->sync_dut(dut);
    difftest->assert_gpr();
    difftest->cycle_preamble();
    do {
      cycle();
      if (dut.io_pc != last_pc) {
        last_pc = dut.io_pc;
        last_pc_count = 0;
      } else {
        last_pc_count++;
        Assert(last_pc_count < CONFIG_SIM_STUCK_THRESHOLD, "simulation cannot progress");
      }
    } while (!dut.io_retired);
    iringbuf.emplace_back(dut.io_pc, dut.io_instr, dut.io_instrCycles);
  } while (!dut.io_break);

  const word_t retval = dut.io_a0;
  if (retval == 0) {
    Log("npc: " ANSI_FMT("HIT GOOD TRAP", ANSI_FG_GREEN) " at pc = " FMT_WORD,
        static_cast<word_t>(dut.io_pc));
  } else {
    assert_fail_msg();
    Log("npc: " ANSI_FMT("HIT BAD TRAP", ANSI_FG_RED) " at pc = " FMT_WORD,
        static_cast<word_t>(dut.io_pc));
  }

  sim_exit();
  return retval != 0;
}