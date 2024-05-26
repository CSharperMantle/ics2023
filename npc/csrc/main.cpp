#include "VTop.h"
#include "debug.hpp"
#include "pmem.hpp"
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <verilated.h>
#include <verilated_vcd_c.h>

#define DUMP_WAVE 1

static const uint32_t DEFAULT_IMG[] = {
    0x00000297, // auipc t0,0
    0x00028823, // sb  zero,16(t0)
    0x0102c503, // lbu a0,16(t0)
    0x00100073, // ebreak (used as nemu_trap)
    0xdeadbeef, // some data
};

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

extern "C" void npc_dpi_ifu(int pc, int *instr) {
  const word_t upc = static_cast<word_t>(pc);
  //printf("PC=0x%08x\n", (uint32_t)pc);
  if (upc > PMEM_LEFT && upc <= PMEM_RIGHT - 4) {
    *instr = *reinterpret_cast<uint32_t *>(guest_to_host(upc));
  } else {
    *instr = 0;
  }
}

extern "C" void npc_dpi_memu(char mem_width,
                             svBit mem_r_en,
                             int mem_r_addr,
                             int *mem_r_data,
                             svBit mem_w_en,
                             int mem_w_addr,
                             int mem_w_data) {
  if (mem_r_en) {
    Log("pc=" FMT_WORD ": MEM RD: " FMT_WORD "; width=%hhd",
        static_cast<word_t>(dut.io_pc),
        static_cast<word_t>(mem_r_addr),
        mem_width);
    switch (mem_width) {
      case 0: *mem_r_data = *reinterpret_cast<uint8_t *>(guest_to_host(mem_r_addr)); break;
      case 1: *mem_r_data = *reinterpret_cast<uint16_t *>(guest_to_host(mem_r_addr)); break;
      case 2: *mem_r_data = *reinterpret_cast<uint32_t *>(guest_to_host(mem_r_addr)); break;
      default: panic("bad mem_width %hhd", mem_width);
    }
  }
  if (mem_w_en) {
    Log("pc=" FMT_WORD ": MEM WR: " FMT_WORD "; width=%hhd; data=" FMT_WORD,
        static_cast<word_t>(dut.io_pc),
        static_cast<word_t>(mem_w_addr),
        mem_width,
        mem_w_data);
    switch (mem_width) {
      case 0: *reinterpret_cast<uint8_t *>(guest_to_host(mem_r_addr)) = mem_w_data & 0xFF; break;
      case 1: *reinterpret_cast<uint16_t *>(guest_to_host(mem_r_addr)) = mem_w_data & 0xFFFF; break;
      case 2: *reinterpret_cast<uint32_t *>(guest_to_host(mem_r_addr)) = mem_w_data; break;
      default: panic("bad mem_width %hhd", mem_width);
    }
  }
}

int main(int argc, char *argv[]) {
  if (argc < 2) {
    Warn("no image file provided, fallback to builtin image");
    std::memcpy(pmem, DEFAULT_IMG, sizeof(DEFAULT_IMG));
  } else {
    FILE *const f_img = std::fopen(argv[1], "rb");
    Assert(f_img, "cannot open \"%s\": errno %d: %s", argv[1], errno, std::strerror(errno));
    std::fseek(f_img, 0, SEEK_END);
    const long len_img = std::ftell(f_img);
    Log("image \"%s\", size=%ld", argv[1], len_img);
    fseek(f_img, 0, SEEK_SET);
    const size_t nbytes_read = fread(guest_to_host(RESET_VECTOR), 1, len_img, f_img);
    Assert(nbytes_read == len_img,
           "cannot read %zu bytes, %zu already read",
           static_cast<size_t>(len_img),
           nbytes_read);
  }

  sim_init();
  dut.reset = 1;
  cycle();
  dut.reset = 0;

  size_t n_instrs = 0;
  do {
    cycle();
    n_instrs++;
  } while (!dut.io_break);

  if (dut.io_break) {
    if (dut.io_a0 == 0) {
      Log("npc: " ANSI_FMT("HIT GOOD TRAP", ANSI_FG_GREEN) " at pc = " FMT_WORD,
          static_cast<word_t>(dut.io_pc));
    } else {
      Log("npc: " ANSI_FMT("HIT BAD TRAP", ANSI_FG_RED) " at pc = " FMT_WORD,
          static_cast<word_t>(dut.io_pc));
    }
  } else {
    Error("hit instr cap");
  }
  sim_exit();

  return 0;
}