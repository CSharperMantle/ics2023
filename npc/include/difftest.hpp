#ifndef NPC_DIFFTEST_HPP_
#define NPC_DIFFTEST_HPP_

#include "common.hpp"
#include "mem/host.hpp"

struct CpuState {
  word_t gpr[32];
  word_t csr[4096];
  paddr_t pc;
  int priv;
  bool intr;
};

extern const char *REG_NAMES[];

enum DiffTestCopyDir { ToDut = 0, ToRef };

extern "C" void (*ref_difftest_memcpy)(paddr_t addr, void *buf, size_t n, bool direction);
extern "C" void (*ref_difftest_regcpy)(void *dut, bool direction);
extern "C" void (*ref_difftest_exec)(uint64_t n);
extern "C" void (*ref_difftest_raise_intr)(uint64_t NO);

void load_difftest(const char *soname, size_t img_size, CpuState &cpu) noexcept;
void difftest_check(const CpuState &ref, const CpuState &dut) noexcept;

#endif /* NPC_DIFFTEST_HPP_ */