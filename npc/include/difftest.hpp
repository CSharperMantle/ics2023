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
    Mip = 0x344,
    Mvendorid = 0xf11,
    Marchid = 0xf12,
    Mimpid = 0xf13,
  };

  DiffTest(const char *soname, size_t img_size);
  ~DiffTest();

  void sync_dut_state(const VDut &vdut);
  void skip_next_ref();
  void step_ref(const VDut &vdut);
  void check_regs(const VDut &vdut);

private:
  struct CpuState {
    word_t gpr[16];
    word_t csr[4096];
    paddr_t pc;
    int priv;
    bool intr;

    enum Priv : int {
      ModeU = 0,
      ModeS = 1,
      ModeM = 3,
    };
  };

  void *dylib = nullptr;
  void (*ref_difftest_memcpy)(paddr_t addr, void *buf, size_t n, bool direction) = nullptr;
  void (*ref_difftest_regcpy)(void *dut, bool direction) = nullptr;
  void (*ref_difftest_exec)(uint64_t n) = nullptr;
  void (*ref_difftest_raise_intr)(uint64_t NO) = nullptr;
  void (*ref_difftest_init)(int port) = nullptr;
  bool skip_next = false;
  void vdut_to_cpu_state(const VDut &vdut);
  CpuState cpu_state;
};

extern const std::array<const char *, 16> REG_NAMES;
extern const std::array<std::tuple<DiffTest::CsrIdx, size_t, const char *>, 11> CSR_MAPPING;

#endif /* NPC_DIFFTEST_HPP_ */