#include <algorithm>
#include <array>
#include <cstring>
#include <dlfcn.h>
#include <tuple>

#include "common.hpp"
#include "debug.hpp"
#include "difftest.hpp"
#include "dpi.hpp"
#include "mem/host.hpp"
#include "verilation.hpp"

const std::array<const char *, 16> REG_NAMES = {
    "$0", "ra", "sp", "gp", "tp", "t0", "t1", "t2", "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5"};

const std::array<std::tuple<DiffTest::CsrIdx, size_t, const char *>, 11> CSR_MAPPING = {
    std::make_tuple(DiffTest::CsrIdx::Mstatus, 0, "mstatus"),
    std::make_tuple(DiffTest::CsrIdx::Mie, 1, "mie"),
    std::make_tuple(DiffTest::CsrIdx::Mtvec, 2, "mtvec"),
    std::make_tuple(DiffTest::CsrIdx::Mscratch, 3, "mscratch"),
    std::make_tuple(DiffTest::CsrIdx::Mepc, 4, "mepc"),
    std::make_tuple(DiffTest::CsrIdx::Mcause, 5, "mcause"),
    std::make_tuple(DiffTest::CsrIdx::Mtval, 6, "mtval"),
    std::make_tuple(DiffTest::CsrIdx::Mip, 7, "mip"),
};

template <typename TSym> static inline void loadsym(void *dl, const char *name, TSym &dst) {
  auto ptr = reinterpret_cast<TSym>(dlsym(dl, name));
  assert(ptr != NULL);
  dst = ptr;
}

DiffTest::DiffTest(const char *soname, size_t img_size) {
  if (soname == nullptr) {
    dylib = nullptr;
    return;
  }

  dylib = dlopen(soname, RTLD_LAZY);
  Assert(dylib != nullptr, "%s", dlerror());
  loadsym(dylib, "difftest_memcpy", ref_difftest_memcpy);
  loadsym(dylib, "difftest_regcpy", ref_difftest_regcpy);
  loadsym(dylib, "difftest_exec", ref_difftest_exec);
  loadsym(dylib, "difftest_raise_intr", ref_difftest_raise_intr);
  loadsym(dylib, "difftest_init", ref_difftest_init);

  ref_difftest_init(0);
  ref_difftest_memcpy(
      RESET_VECTOR, flash_guest_to_host(RESET_VECTOR), img_size, static_cast<bool>(CopyDir::ToRef));
}

DiffTest::~DiffTest() {
  if (dylib == nullptr) {
    return;
  }
  dlclose(dylib);
}

void DiffTest::sync_dut_state(const VDut &vdut) {
  if (dylib == nullptr) {
    return;
  }
  vdut_to_cpu_state(vdut);
}

void DiffTest::skip_next_ref() {
  if (dylib == nullptr) {
    return;
  }
  skip_next = true;
}

void DiffTest::step_ref(const VDut &vdut) {
  if (dylib == nullptr) {
    return;
  }

  CpuState ref;
  ref_difftest_regcpy(&ref, static_cast<bool>(CopyDir::ToDut));

  if (skip_next) {
    vdut_to_cpu_state(vdut);
    ref_difftest_regcpy(&cpu_state, static_cast<bool>(CopyDir::ToRef));
    skip_next = false;
    return;
  } else {
    ref_difftest_exec(1);
  }
}

void DiffTest::check_regs(const VDut &vdut) {
  if (dylib == nullptr) {
    return;
  }
  bool mismatch = false;

  vdut_to_cpu_state(vdut);
  CpuState ref;
  ref_difftest_regcpy(&ref, static_cast<bool>(CopyDir::ToDut));

  if (ref.pc != cpu_state.pc) {
    mismatch = true;
    Error("pc mismatch: ref=" FMT_WORD ", dut=" FMT_WORD, ref.pc, cpu_state.pc);
  }
  for (size_t i = 0; i < ARRLEN(ref.gpr); i++) {
    if (ref.gpr[i] != cpu_state.gpr[i]) {
      mismatch = true;
      Error("pc=" FMT_WORD ": gpr %s mismatch: ref=" FMT_WORD ", dut=" FMT_WORD,
            cpu_state.pc,
            REG_NAMES[i],
            ref.gpr[i],
            cpu_state.gpr[i]);
    }
  }
  Assert(!mismatch, "state mismatch; aborting");
}

static word_t get_csr(const VDut &vdut, size_t idx) {
  Assert(idx >= 0 && idx <= 7, "csr index (%zu) is out of range", idx);
  switch (idx) {
    case 0:
      return vdut.rootp
          ->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__csr__DOT__csrs_0;
    case 1:
      return vdut.rootp
          ->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__csr__DOT__csrs_1;
    case 2:
      return vdut.rootp
          ->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__csr__DOT__csrs_2;
    case 3:
      return vdut.rootp
          ->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__csr__DOT__csrs_3;
    case 4:
      return vdut.rootp
          ->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__csr__DOT__csrs_4;
    case 5:
      return vdut.rootp
          ->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__csr__DOT__csrs_5;
    case 6:
      return vdut.rootp
          ->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__csr__DOT__csrs_6;
    case 7:
      return vdut.rootp
          ->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__csr__DOT__csrs_7;
    default: __builtin_unreachable();
  }
}

void DiffTest::vdut_to_cpu_state(const VDut &vdut) {
  if (dylib == nullptr) {
    return;
  }
  memcpy(
      cpu_state.gpr,
      vdut.rootp
          ->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__gpr__DOT__regs_sram_ext__DOT__Memory
          .data(),
      sizeof(cpu_state.gpr));
  cpu_state.pc =
      vdut.rootp->ysyxSoCFull__DOT__asic__DOT__cpu__DOT__cpu__DOT__core__DOT__ifu__DOT__pc;
  cpu_state.priv = CpuState::Priv::ModeM;
  for (const auto &p : CSR_MAPPING) {
    cpu_state.csr[static_cast<size_t>(std::get<0>(p))] = get_csr(vdut, std::get<1>(p));
  }
}
