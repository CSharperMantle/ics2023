#include <algorithm>
#include <array>
#include <cstring>
#include <dlfcn.h>
#include <tuple>

#include "common.hpp"
#include "debug.hpp"
#include "difftest.hpp"
#include "mem/host.hpp"
#include "verilation.hpp"

const std::array<const char *, 32> REG_NAMES{"$0", "ra", "sp",  "gp",  "tp", "t0", "t1", "t2",
                                             "s0", "s1", "a0",  "a1",  "a2", "a3", "a4", "a5",
                                             "a6", "a7", "s2",  "s3",  "s4", "s5", "s6", "s7",
                                             "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"};

const std::array<std::tuple<DiffTest::CsrIdx, size_t, const char *>, 9> CSR_MAPPING{
    std::make_tuple(DiffTest::Satp, 0, "satp"),
    std::make_tuple(DiffTest::Mstatus, 1, "mstatus"),
    std::make_tuple(DiffTest::Mie, 2, "mie"),
    std::make_tuple(DiffTest::Mtvec, 3, "mtvec"),
    std::make_tuple(DiffTest::Mscratch, 4, "mscratch"),
    std::make_tuple(DiffTest::Mepc, 5, "mepc"),
    std::make_tuple(DiffTest::Mcause, 6, "mcause"),
    std::make_tuple(DiffTest::Mtval, 7, "mtval"),
    std::make_tuple(DiffTest::Mip, 8, "mip"),
};

DiffTest::DiffTest(const char *soname, size_t img_size) {
  dylib = nullptr;
  return;

#if 0
  if (soname == nullptr) {
    dylib = nullptr;
    return;
  }

  dylib = dlopen(soname, RTLD_LAZY);
  Assert(dylib != nullptr, "%s", dlerror());
  ref_difftest_memcpy =
      reinterpret_cast<decltype(ref_difftest_memcpy)>(dlsym(dylib, "difftest_memcpy"));
  assert(ref_difftest_memcpy);
  ref_difftest_regcpy =
      reinterpret_cast<decltype(ref_difftest_regcpy)>(dlsym(dylib, "difftest_regcpy"));
  assert(ref_difftest_regcpy);
  ref_difftest_exec = reinterpret_cast<decltype(ref_difftest_exec)>(dlsym(dylib, "difftest_exec"));
  assert(ref_difftest_exec);
  ref_difftest_raise_intr =
      reinterpret_cast<decltype(ref_difftest_raise_intr)>(dlsym(dylib, "difftest_raise_intr"));
  assert(ref_difftest_raise_intr);
  void (*ref_difftest_init)(int) =
      reinterpret_cast<decltype(ref_difftest_init)>(dlsym(dylib, "difftest_init"));
  assert(ref_difftest_init);

  ref_difftest_init(0);
  ref_difftest_memcpy(RESET_VECTOR, guest_to_host(RESET_VECTOR), img_size, CopyDir::ToRef);
#endif
}

DiffTest::~DiffTest() {
  if (dylib == nullptr) {
    return;
  }
  dlclose(dylib);
}

void DiffTest::skip_next() {
  if (dylib == nullptr) {
    return;
  }
  auto p_ctr =
      std::find_if(skip_ctr.begin(), skip_ctr.end(), [](int ctr) { return ctr == SKIP_CTR_FREE; });
  Assert(p_ctr != skip_ctr.end(), "unreachable");
  *p_ctr = SKIP_CTR_START;
}

void DiffTest::assert_gpr() {
  if (dylib == nullptr) {
    return;
  }
  if (skip_check) {
    skip_check = false;
    return;
  }
  // Assert(ref.pc == dut.pc, "pc mismatch: ref=" FMT_WORD ", dut=" FMT_WORD, ref.pc, dut.pc);
  for (size_t i = 0; i < 32; i++) {
    Assert(ref.gpr[i] == dut.gpr[i],
           "pc=" FMT_WORD ": gpr \"%s\" mismatch: ref=" FMT_WORD ", dut=" FMT_WORD,
           dut.pc,
           REG_NAMES[i],
           ref.gpr[i],
           dut.gpr[i]);
  }
}

void DiffTest::sync_dut(const VDut &vdut) {
  if (dylib == nullptr) {
    return;
  }
  Assert(0, "difftest not implemented for soc");
#if 0
  memcpy(dut.gpr,
         vdut.rootp->Top__DOT__core__DOT__gpr__DOT__regs_ext__DOT__Memory.data(),
         sizeof(dut.gpr));
  for (const auto &p : CSR_MAPPING) {
    dut.csr[std::get<0>(p)] =
        vdut.rootp->Top__DOT__core__DOT__csr__DOT__csrs_ext__DOT__Memory.data()[std::get<1>(p)];
  }
  dut.pc = vdut_dpi_state.pc;
#endif
}

void DiffTest::cycle_preamble() {
  if (dylib == nullptr) {
    return;
  }
  skip_cycle = false;
  for (auto &ctr : skip_ctr) {
    if (ctr == SKIP_CTR_FREE) {
      continue;
    } else if (ctr == 0) {
      skip_check = skip_cycle = true;
    }
    ctr--;
  }
  ref_difftest_regcpy(const_cast<CpuState *>(&dut), CopyDir::ToRef);
}

void DiffTest::cycle() {
  if (dylib == nullptr) {
    return;
  }
  if (skip_cycle) {
    ref_difftest_regcpy(&ref, CopyDir::ToDut);
    skip_cycle = false;
  } else {
    ref_difftest_exec(1);
    ref_difftest_regcpy(&ref, CopyDir::ToDut);
  }
}
