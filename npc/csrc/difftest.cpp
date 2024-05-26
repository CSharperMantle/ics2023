#include "difftest.hpp"
#include "VTop.h"
#include "VTop__Syms.h"
#include "common.hpp"
#include "debug.hpp"
#include "pmem.hpp"
#include <dlfcn.h>

const char *REG_NAMES[] = {"$0", "ra", "sp", "gp", "tp",  "t0",  "t1", "t2", "s0", "s1", "a0",
                           "a1", "a2", "a3", "a4", "a5",  "a6",  "a7", "s2", "s3", "s4", "s5",
                           "s6", "s7", "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"};

static bool difftest_loaded = false;

void (*ref_difftest_memcpy)(paddr_t addr, void *buf, size_t n, bool direction) = nullptr;
void (*ref_difftest_regcpy)(void *dut, bool direction) = nullptr;
void (*ref_difftest_exec)(uint64_t n) = nullptr;
void (*ref_difftest_raise_intr)(uint64_t NO) = nullptr;

static void stub_difftest_memcpy(paddr_t addr, void *buf, size_t n, bool direction) {}
static void stub_difftest_regcpy(void *dut, bool direction) {}
static void stub_difftest_exec(uint64_t n) {}
static void stub_difftest_raise_intr(uint64_t NO) {}

void load_difftest(const char *soname, size_t img_size, CpuState &cpu) noexcept {
  if (soname == nullptr) {
    Log("difftest disabled");
    ref_difftest_memcpy = stub_difftest_memcpy;
    ref_difftest_regcpy = stub_difftest_regcpy;
    ref_difftest_exec = stub_difftest_exec;
    ref_difftest_raise_intr = stub_difftest_raise_intr;
    return;
  }

  Log("difftest enabled with ref \"%s\"", soname);
  void *const handle = dlopen(soname, RTLD_LAZY);
  assert(handle);
  ref_difftest_memcpy =
      reinterpret_cast<decltype(ref_difftest_memcpy)>(dlsym(handle, "difftest_memcpy"));
  assert(ref_difftest_memcpy);
  ref_difftest_regcpy =
      reinterpret_cast<decltype(ref_difftest_regcpy)>(dlsym(handle, "difftest_regcpy"));
  assert(ref_difftest_regcpy);
  ref_difftest_exec = reinterpret_cast<decltype(ref_difftest_exec)>(dlsym(handle, "difftest_exec"));
  assert(ref_difftest_exec);
  ref_difftest_raise_intr =
      reinterpret_cast<decltype(ref_difftest_raise_intr)>(dlsym(handle, "difftest_raise_intr"));
  assert(ref_difftest_raise_intr);
  void (*ref_difftest_init)(int) =
      reinterpret_cast<decltype(ref_difftest_init)>(dlsym(handle, "difftest_init"));
  assert(ref_difftest_init);

  ref_difftest_init(0);
  ref_difftest_memcpy(RESET_VECTOR, guest_to_host(RESET_VECTOR), img_size, DiffTestCopyDir::ToRef);
  ref_difftest_regcpy(&cpu, DiffTestCopyDir::ToRef);

  difftest_loaded = true;
}

void difftest_check(const CpuState &ref, const CpuState &dut) noexcept {
  if (!difftest_loaded) {
    return;
  }

  Assert(ref.pc == dut.pc, "PC mismatch: Ref=" FMT_WORD ", Dut=" FMT_WORD, ref.pc, dut.pc);
  for (size_t i = 0; i < 32; i++) {
    Assert(ref.gpr[i] == dut.gpr[i],
           "PC=" FMT_WORD ": GPR \"%s\" mismatch: Ref=" FMT_WORD ", Dut=" FMT_WORD,
           dut.pc,
           REG_NAMES[i],
           ref.gpr[i],
           dut.gpr[i]);
  }
}