#ifndef NPC_DIFFTEST_HPP_
#define NPC_DIFFTEST_HPP_

#include <array>
#include <utility>

#include "common.hpp"
#include "mem/host.hpp"
#include "verilation.hpp"

struct DiffTest {
  enum CopyDir { ToDut = 0, ToRef };
  enum CsrIdx {
    Satp = 0x180,
    Mstatus = 0x300,
    Mie = 0x304,
    Mtvec = 0x305,
    Mscratch = 0x340,
    Mepc = 0x341,
    Mcause = 0x342,
    Mtval = 0x343,
    Mip = 0x344
  };
  struct CpuState {
    word_t gpr[32];
    word_t csr[4096];
    paddr_t pc;
    int priv;
    bool intr;
  };

  DiffTest(const char *soname, size_t img_size);
  ~DiffTest();

  void skip_next();
  void assert_gpr();
  void cycle_preamble();
  void sync_dut(const VDut &vdut);
  void cycle();

private:
  void *dylib = nullptr;
  void (*ref_difftest_memcpy)(paddr_t addr, void *buf, size_t n, bool direction) = nullptr;
  void (*ref_difftest_regcpy)(void *dut, bool direction) = nullptr;
  void (*ref_difftest_exec)(uint64_t n) = nullptr;
  void (*ref_difftest_raise_intr)(uint64_t NO) = nullptr;
  bool skip_cycle;
  bool skip_check;
  CpuState dut;
  CpuState ref;
  static constexpr int SKIP_CTR_FREE = -1;
  static constexpr int SKIP_CTR_START = 2;
  std::array<int, 3> skip_ctr{SKIP_CTR_FREE, SKIP_CTR_FREE, SKIP_CTR_FREE};
};

extern const std::array<const char *, 32> REG_NAMES;
extern const std::array<std::tuple<DiffTest::CsrIdx, size_t, const char *>, 9> CSR_MAPPING;

#endif /* NPC_DIFFTEST_HPP_ */