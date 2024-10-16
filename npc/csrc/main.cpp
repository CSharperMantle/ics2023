
#include <algorithm>
#include <cerrno>
#include <chrono>
#include <cinttypes>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <nvboard.h>
#include <type_traits>

#include "common.hpp"
#include "debug.hpp"
#include "difftest.hpp"
#include "dpi.hpp"
#include "mem/host.hpp"
#include "util/disasm.hpp"
#include "util/iringbuf.hpp"
#include "verilation.hpp"

extern void nvboard_bind_all_pins(VDut *);

static const uint32_t DEFAULT_IMG[] = {
    0x00000297, // auipc t0,0
    0x00028823, // sb  zero,16(t0)
    0x0102c503, // lbu a0,16(t0)
    0x00100073, // ebreak (used as nemu_trap)
    0xdeadbeef, // some data
};

VDut dut{};

std::unique_ptr<DiffTest> difftest{};

static VerilatedContext *ctx = nullptr;
static VerilatedVcdC *tf = nullptr;

static uint64_t cycles = 0;

static void step_and_dump_wave() {
  dut.eval();
  ctx->timeInc(1);
#if defined(CONFIG_DUMP_WAVE) && CONFIG_DUMP_WAVE
  tf->dump(ctx->time());
#endif
}

static void cycle() {
  dut.clock = 1;
  step_and_dump_wave();
  dut.clock = 0;
  step_and_dump_wave();
  nvboard_update();
  cycles++;
}

static void sim_init(int argc, char *argv[]) {
  Verilated::commandArgs(argc, argv);
  ctx = Verilated::defaultContextp();
  tf = new VerilatedVcdC();
#if defined(CONFIG_DUMP_WAVE) && CONFIG_DUMP_WAVE
  ctx->traceEverOn(true);
  dut.trace(tf, 0);
  tf->open("dump.vcd");
#endif
  cycles = 0;
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
    Log("%" PRIu16 "\t" FMT_WORD "\t%s",
        std::get<2>(instr),
        std::get<0>(instr),
        instr_disasm.c_str());
  }
  Log("- - - %d recent instructions (bottom: newest)", CONFIG_IRINGBUF_NR_ELEM);
}

void assert_fail_msg() {
  print_iringbuf();
#if defined(CONFIG_DUMP_WAVE) && CONFIG_DUMP_WAVE
  tf->close();
#endif
}

template <typename T1,
          typename T2,
          typename T3,
          typename = std::enable_if_t<std::is_arithmetic<T1>::value>>
static constexpr bool is_between(T1 x, T2 min, T3 max) noexcept {
  using common_t = std::common_type_t<T1, T2, T3>;
  return static_cast<common_t>(x) >= static_cast<common_t>(min)
         && static_cast<common_t>(x) < static_cast<common_t>(max);
}

int main(int argc, char *argv[]) {
  std::setbuf(stdout, NULL);

  size_t len_img = 0;
  if (argc < 2) {
    Warn("no image file provided, fallback to builtin image");
    std::memcpy(flash, DEFAULT_IMG, sizeof(DEFAULT_IMG));
    len_img = sizeof(DEFAULT_IMG);
  } else {
    FILE *const f_img = std::fopen(argv[1], "rb");
    Assert(f_img, "cannot open \"%s\": errno %d: %s", argv[1], errno, std::strerror(errno));
    std::fseek(f_img, 0, SEEK_END);
    len_img = static_cast<size_t>(std::ftell(f_img));
    Log("image \"%s\", size=%zu", argv[1], len_img);
    if (len_img > sizeof(flash)) {
      Warn("image too large, will be clipped to %zu", sizeof(flash));
    }
    fseek(f_img, 0, SEEK_SET);
    const size_t nbytes_read =
        fread(flash_guest_to_host(RESET_VECTOR), 1, std::min(sizeof(flash), len_img), f_img);
    Assert(nbytes_read == len_img, "cannot read %zu bytes, %zu already read", len_img, nbytes_read);
  }

  const char *env_ref_so = getenv("NPC_DIFFTEST_REF_SO");
  if (env_ref_so == nullptr || std::strlen(env_ref_so) == 0) {
    Log("difftest: not initialized; NPC_DIFFTEST_REF_SO=%s",
        env_ref_so == nullptr ? "(null)" : env_ref_so);
    difftest = std::make_unique<DiffTest>(nullptr, len_img);
  } else {
    Log("difftest: loading ref so \"%s\"", env_ref_so);
    difftest = std::make_unique<DiffTest>(env_ref_so, len_img);
  }

  init_disasm("riscv32-pc-linux-gnu");

  sim_init(argc, argv);
  nvboard_bind_all_pins(&dut);
  nvboard_init(1);
  dut.reset = 1;
  for (size_t i = 0; i < 16; i++) {
    cycle();
  }
  cycle();
  dut.reset = 0;

  difftest->sync_dut_state(dut);

  static word_t last_pc = 0;
  static size_t last_pc_count = 0;

  do {
    do {
      cycle();
      if (dut_dpi_state.pc != last_pc) {
        last_pc = dut_dpi_state.pc;
        last_pc_count = 0;
      } else {
        last_pc_count++;
        Assert(last_pc_count < CONFIG_SIM_STUCK_THRESHOLD, "%s", "simulation cannot progress");
      }
    } while (!dut_dpi_state.retired);

    if (dut_dpi_state.mem_en
        && std::none_of(
            REF_MEM_BACKED_AREAS.cbegin(), REF_MEM_BACKED_AREAS.cend(), [=](const auto &area) {
              return is_between(
                  dut_dpi_state.rw_addr, std::get<1>(area), std::get<1>(area) + std::get<2>(area));
            })) {
      difftest->skip_next_ref();
    }
    difftest->step_ref(dut);
    difftest->check_regs(dut);

    iringbuf.emplace_back(dut_dpi_state.pc, dut_dpi_state.instr, dut_dpi_state.instr_cycles);
    Assert(!dut_dpi_state.bad, "%s", "instruction retired as invalid");
  } while (!dut_dpi_state.ebreak);

  const word_t retval = dut_dpi_state.reg_a0;
  if (retval == 0) {
    Log("npc: " ANSI_FMT("HIT GOOD TRAP", ANSI_FG_GREEN) " at pc = " FMT_WORD "; %" PRIu64
                                                         " cycles",
        dut_dpi_state.pc,
        cycles);
  } else {
    assert_fail_msg();
    Log("npc: " ANSI_FMT("HIT BAD TRAP", ANSI_FG_RED) " (" FMT_WORD ") at pc = " FMT_WORD,
        dut_dpi_state.reg_a0,
        dut_dpi_state.pc);
  }

  nvboard_quit();
  sim_exit();
  return retval != 0;
}
