#ifndef NPC_UTIL_DISASM_HPP_
#define NPC_UTIL_DISASM_HPP_

#include <cstdint>
#include <string>

void init_disasm(const char *triple);
std::string disasm(uint64_t pc, const uint8_t *code, int nbyte);

#endif /* NPC_UTIL_DISASM_HPP_ */