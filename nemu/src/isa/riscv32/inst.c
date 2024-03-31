/***************************************************************************************
* Copyright (c) 2014-2022 Zihao Yu, Nanjing University
*
* NEMU is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

#include "common.h"
#include "isa.h"
#include "local-include/reg.h"
#include <cpu/cpu.h>
#include <cpu/ifetch.h>
#include <cpu/decode.h>

#define R(i)   gpr(i)
#define CSR(i) csr(i)
#define Mr     vaddr_read
#define Mw     vaddr_write

enum {
  TYPE_R,
  TYPE_I,
  TYPE_IS,
  TYPE_ICSR,
  TYPE_S,
  TYPE_B,
  TYPE_U,
  TYPE_J,
  TYPE_N, // none
};

#define src1R()   do { *src1 = R(rs1); } while (0)
#define src2R()   do { *src2 = R(rs2); } while (0)
#define immI()    do { *imm = SEXT(BITS(i, 31, 20), 12); } while (0)
#define immIS()   do { *imm = BITS(i, 25, 20); } while (0)
#define immICSR() do { *imm = BITS(i, 31, 20); } while (0)
#define immS()    do { *imm = (SEXT(BITS(i, 31, 25), 7) << 5) | BITS(i, 11, 7); } while (0)
#define immB()    do { *imm = SEXT((BITS(i, 31, 31) << 12) | (BITS(i, 7, 7) << 11) | (BITS(i, 30, 25) << 5) | (BITS(i, 11, 8) << 1), 13); } while (0)
#define immU()    do { *imm = SEXT(BITS(i, 31, 12), 20) << 12; } while (0)
#define immJ()    do { *imm = SEXT((BITS(i, 31, 31) << 20) | (BITS(i, 19, 12) << 12) | (BITS(i, 20, 20) << 11) | (BITS(i, 30, 21) << 1), 21); } while (0)

static uint64_t carry_add_u64(uint64_t a, uint64_t b, bool *out_c) {
  const uint64_t sum = a + b;
  *out_c = sum < a || sum < b;
  return sum;
}

static uint64_t full_mul_u64(uint64_t a, uint64_t b, uint64_t *out_h) {
  const uint64_t al = a & 0xFFFFFFFF;
  const uint64_t ah = a >> 32;
  const uint64_t bl = b & 0xFFFFFFFF;
  const uint64_t bh = b >> 32;

  const uint64_t p0 = al * bl;
  const uint64_t p1 = ah * bl;
  const uint64_t p2 = al * bh;
  const uint64_t p3 = ah * bh;

  bool c0, c1;
  uint64_t sum_l, sum_h;
  sum_l = carry_add_u64(p0, p1 << 32, &c0);
  sum_l = carry_add_u64(sum_l, p2 << 32, &c1);
  sum_h = (uint64_t)c0 + (uint64_t)c1;
  sum_h += p1 >> 32;
  sum_h += p2 >> 32;
  sum_h += p3;

  *out_h = sum_h;
  return sum_l;
}

static int64_t high_mul_i64(int64_t a, int64_t b) {
  int64_t h;
  full_mul_u64((uint64_t)a, (uint64_t)b, (uint64_t *)&h);
  const int64_t t1 = (a >> 63) & b;
  const int64_t t2 = (b >> 63) & a;
  return h - t1 - t2;
}

#define MRET_UPD_MSTATUS()                                                                         \
  do {                                                                                             \
    CsrMstatus_t mstatus = {.packed = CSR(CSR_IDX_MSTATUS)};                                       \
    mstatus.mie = mstatus.mpie;                                                                    \
    mstatus.mpie = 1;                                                                              \
    CSR(CSR_IDX_MSTATUS) = mstatus.packed;                                                         \
  } while (0)

static void decode_operand(Decode *s, int *rd, word_t *src1, word_t *src2, word_t *imm, int type) {
  uint32_t i = s->isa.inst.val;
  int rs1 = BITS(i, 19, 15);
  int rs2 = BITS(i, 24, 20);
  *rd     = BITS(i, 11, 7);
  switch (type) {
    case TYPE_R:    src1R(); src2R();            break;
    case TYPE_I:    src1R();          immI();    break;
    case TYPE_IS:   src1R();          immIS();   break;
    case TYPE_ICSR: src1R();          immICSR(); break;
    case TYPE_S:    src1R(); src2R(); immS();    break;
    case TYPE_B:    src1R(); src2R(); immB();    break;
    case TYPE_U:                      immU();    break;
    case TYPE_J:                      immJ();    break;
  }
}

static int decode_exec(Decode *s) {
  int rd = 0;
  word_t src1 = 0, src2 = 0, imm = 0;
  s->dnpc = s->snpc;
#ifdef CONFIG_FTRACE
  s->isa.is_jal = false;
  s->isa.is_jalr = false;
#endif

#define INSTPAT_INST(s) ((s)->isa.inst.val)
#define INSTPAT_MATCH(s, name, type, ... /* execute body */ ) { \
  decode_operand(s, &rd, &src1, &src2, &imm, concat(TYPE_, type)); \
  __VA_ARGS__ ; \
}

  INSTPAT_START();

  // -*- RV64I: Integer base instructions -*-
  // INTEGER COMPUTATIONAL INSTRUCTIONS
  // Arithmetic
  INSTPAT("0000000 ????? ????? 000 ????? 01100 11", add    ,    R, R(rd) = src1 + src2);
  INSTPAT("0000000 ????? ????? 000 ????? 01110 11", addw   ,    R, R(rd) = SEXT((uint32_t)(src1 + src2), 32));
  INSTPAT("??????? ????? ????? 000 ????? 00100 11", addi   ,    I, R(rd) = src1 + imm);
  INSTPAT("??????? ????? ????? 000 ????? 00110 11", addiw  ,    I, R(rd) = SEXT((uint32_t)(src1 + imm), 32));
  INSTPAT("0100000 ????? ????? 000 ????? 01100 11", sub    ,    R, R(rd) = src1 - src2);
  INSTPAT("0100000 ????? ????? 000 ????? 01110 11", subw   ,    R, R(rd) = SEXT((uint32_t)(src1 - src2), 32));
  INSTPAT("??????? ????? ????? ??? ????? 01101 11", lui    ,    U, R(rd) = imm);
  INSTPAT("??????? ????? ????? ??? ????? 00101 11", auipc  ,    U, R(rd) = s->pc + imm);
  // Logical
  INSTPAT("0000000 ????? ????? 100 ????? 01100 11", xor    ,    R, R(rd) = src1 ^ src2);
  INSTPAT("??????? ????? ????? 100 ????? 00100 11", xori   ,    I, R(rd) = src1 ^ imm);
  INSTPAT("0000000 ????? ????? 110 ????? 01100 11", or     ,    R, R(rd) = src1 | src2);
  INSTPAT("??????? ????? ????? 110 ????? 00100 11", ori    ,    I, R(rd) = src1 | imm);
  INSTPAT("0000000 ????? ????? 111 ????? 01100 11", and    ,    R, R(rd) = src1 & src2);
  INSTPAT("??????? ????? ????? 111 ????? 00100 11", andi   ,    I, R(rd) = src1 & imm);
  // Shifts
  INSTPAT("0000000 ????? ????? 001 ????? 01100 11", sll    ,    R, R(rd) = src1 << src2);
  INSTPAT("0000000 ????? ????? 001 ????? 01110 11", sllw   ,    R, R(rd) = SEXT((uint32_t)(src1 << (src2 & 0x1F)), 32));
  INSTPAT("000000? ????? ????? 001 ????? 00100 11", slli   ,   IS, R(rd) = src1 << imm);
  INSTPAT("0000000 ????? ????? 001 ????? 00110 11", slliw  ,   IS, R(rd) = SEXT((uint32_t)(src1 << imm), 32));
  INSTPAT("0000000 ????? ????? 101 ????? 01100 11", srl    ,    R, R(rd) = src1 >> src2);
  INSTPAT("0000000 ????? ????? 101 ????? 01110 11", srlw   ,    R, R(rd) = SEXT((uint32_t)src1 >> (src2 & 0x1F), 32));
  INSTPAT("000000? ????? ????? 101 ????? 00100 11", srli   ,   IS, R(rd) = src1 >> imm);
  INSTPAT("0000000 ????? ????? 101 ????? 00110 11", srliw  ,   IS, R(rd) = SEXT((uint32_t)src1 >> imm, 32));
  INSTPAT("0100000 ????? ????? 101 ????? 01100 11", sra    ,    R, R(rd) = (sword_t)src1 >> src2);
  INSTPAT("0100000 ????? ????? 101 ????? 01110 11", sraw   ,    R, R(rd) = SEXT((int32_t)src1 >> (src2 & 0x1F), 32));
  INSTPAT("010000? ????? ????? 101 ????? 00100 11", srai   ,   IS, R(rd) = (sword_t)src1 >> imm);
  INSTPAT("0100000 ????? ????? 101 ????? 00110 11", sraiw  ,   IS, R(rd) = SEXT((int32_t)src1 >> imm, 32));
  // Compare
  INSTPAT("0000000 ????? ????? 010 ????? 01100 11", slt    ,    R, R(rd) = (sword_t)src1 < (sword_t)src2 ? 1 : 0);
  INSTPAT("??????? ????? ????? 010 ????? 00100 11", slti   ,    I, R(rd) = (sword_t)src1 < (sword_t)SEXT(imm, 12) ? 1 : 0);
  INSTPAT("0000000 ????? ????? 011 ????? 01100 11", sltu   ,    R, R(rd) = src1 < src2 ? 1 : 0);
  INSTPAT("??????? ????? ????? 011 ????? 00100 11", sltiu  ,    I, R(rd) = src1 < SEXT(imm, 12) ? 1 : 0);

  // LOADS AND STORES
  // Loads
  INSTPAT("??????? ????? ????? 000 ????? 00000 11", lb     ,    I, R(rd) = SEXT(Mr(src1 + imm, 1), 8));
  INSTPAT("??????? ????? ????? 001 ????? 00000 11", lh     ,    I, R(rd) = SEXT(Mr(src1 + imm, 2), 16));
  INSTPAT("??????? ????? ????? 010 ????? 00000 11", lw     ,    I, R(rd) = SEXT(Mr(src1 + imm, 4), 32));
  INSTPAT("??????? ????? ????? 011 ????? 00000 11", ld     ,    I, R(rd) = Mr(src1 + imm, 8));
  INSTPAT("??????? ????? ????? 100 ????? 00000 11", lbu    ,    I, R(rd) = Mr(src1 + imm, 1));
  INSTPAT("??????? ????? ????? 101 ????? 00000 11", lhu    ,    I, R(rd) = Mr(src1 + imm, 2));
  INSTPAT("??????? ????? ????? 110 ????? 00000 11", lwu    ,    I, R(rd) = Mr(src1 + imm, 4));
  // Stores
  INSTPAT("??????? ????? ????? 000 ????? 01000 11", sb     ,    S, Mw(src1 + imm, 1, src2));
  INSTPAT("??????? ????? ????? 001 ????? 01000 11", sh     ,    S, Mw(src1 + imm, 2, src2));
  INSTPAT("??????? ????? ????? 010 ????? 01000 11", sw     ,    S, Mw(src1 + imm, 4, src2));
  INSTPAT("??????? ????? ????? 011 ????? 01000 11", sd     ,    S, Mw(src1 + imm, 8, src2));

  // CONTROL TRANSFERS
  // Branches
  INSTPAT("??????? ????? ????? 000 ????? 11000 11", beq    ,    B, if (src1 == src2) { s->dnpc = s->pc + imm; });
  INSTPAT("??????? ????? ????? 001 ????? 11000 11", bne    ,    B, if (src1 != src2) { s->dnpc = s->pc + imm; });
  INSTPAT("??????? ????? ????? 100 ????? 11000 11", blt    ,    B, if ((sword_t)src1 < (sword_t)src2) { s->dnpc = s->pc + imm; });
  INSTPAT("??????? ????? ????? 101 ????? 11000 11", bge    ,    B, if ((sword_t)src1 >= (sword_t)src2) { s->dnpc = s->pc + imm; });
  INSTPAT("??????? ????? ????? 110 ????? 11000 11", bltu   ,    B, if (src1 < src2) { s->dnpc = s->pc + imm; });
  INSTPAT("??????? ????? ????? 111 ????? 11000 11", bgeu   ,    B, if (src1 >= src2) { s->dnpc = s->pc + imm; });
  // Jump & Link
#ifdef CONFIG_FTRACE
  INSTPAT("??????? ????? ????? ??? ????? 11011 11", jal    ,    J, R(rd) = s->pc + 4; s->dnpc = s->pc + imm; s->isa.is_jal = true);
  INSTPAT("??????? ????? ????? 000 ????? 11001 11", jalr   ,    I, word_t t = s->pc + 4; s->dnpc = (src1 + imm) & ~(word_t)1; R(rd) = t; s->isa.is_jalr = true);
#else   
  INSTPAT("??????? ????? ????? ??? ????? 11011 11", jal    ,    J, R(rd) = s->pc + 4; s->dnpc = s->pc + imm);
  INSTPAT("??????? ????? ????? 000 ????? 11001 11", jalr   ,    I, word_t t = s->pc + 4; s->dnpc = (src1 + imm) & ~(word_t)1; R(rd) = t);
#endif
  // MEMORY ORDERING
  // Sync
  // fence
  // fence.i

  // ENVIRONMENTAL CALLS & BREAKPOINTS
  // System
  INSTPAT("0000000 00000 00000 000 00000 11100 11", ecall  ,    N, s->dnpc = isa_raise_intr(EXCP_M_ENV_CALL, s->pc));
  INSTPAT("0000000 00001 00000 000 00000 11100 11", ebreak ,    N, NEMUTRAP(s->pc, R(10))); // R(10) is $a0
  // Trap-Return
  INSTPAT("0011000 00010 00000 000 00000 11100 11", mret   ,    R, s->dnpc = CSR(CSR_IDX_MEPC); MRET_UPD_MSTATUS());

  // COUNTERS
  // rdcycle
  // rdcycleh
  // rdtime
  // rdtimeh
  // rdinstret
  // rdinstreth

  // -*- RV64M: Integer multiplication and division -*-
  INSTPAT("0000001 ????? ????? 000 ????? 01100 11", mul    ,    R, R(rd) = src1 * src2);
  INSTPAT("0000001 ????? ????? 001 ????? 01100 11", mulh   ,    R, R(rd) = high_mul_i64((sword_t)src1, (sword_t)src2));
  // mulhsu
  INSTPAT("0000001 ????? ????? 011 ????? 01100 11", mulhu  ,    R, word_t t; full_mul_u64(src1, src2, &t); R(rd) = t);
  INSTPAT("0000001 ????? ????? 000 ????? 01110 11", mulw   ,    R, R(rd) = SEXT((uint32_t)(src1 * src2), 32));
  INSTPAT("0000001 ????? ????? 100 ????? 01100 11", div    ,    R, R(rd) = (sword_t)src1 / (sword_t)src2);
  INSTPAT("0000001 ????? ????? 100 ????? 01110 11", divw   ,    R, R(rd) = SEXT((int32_t)src1 / (int32_t)src2, 32));
  INSTPAT("0000001 ????? ????? 101 ????? 01100 11", divu   ,    R, R(rd) = src1 / src2);
  INSTPAT("0000001 ????? ????? 101 ????? 01110 11", divuw  ,    R, R(rd) = SEXT((uint32_t)src1 / (uint32_t)src2, 32));
  INSTPAT("0000001 ????? ????? 110 ????? 01100 11", rem    ,    R, R(rd) = (sword_t)src1 % (sword_t)src2);
  INSTPAT("0000001 ????? ????? 110 ????? 01110 11", remw   ,    R, R(rd) = SEXT((int32_t)src1 % (int32_t)src2, 32));
  INSTPAT("0000001 ????? ????? 111 ????? 01100 11", remu   ,    R, R(rd) = src1 % src2);
  INSTPAT("0000001 ????? ????? 111 ????? 01110 11", remuw  ,    R, R(rd) = SEXT((uint32_t)src1 % (uint32_t)src2, 32));

  // -*- RV64Zicsr: Control and status register (CSR), version 2.0 -*-
  INSTPAT("??????? ????? ????? 001 ????? 11100 11", csrrw  , ICSR, word_t t = CSR(imm); CSR(imm) = src1; R(rd) = t;);
  // csrrwi
  INSTPAT("??????? ????? ????? 010 ????? 11100 11", csrrs  , ICSR, word_t t = CSR(imm); CSR(imm) = t | src1; R(rd) = t);
  // csrrsi
  INSTPAT("??????? ????? ????? 011 ????? 11100 11", csrrc  , ICSR, word_t t = CSR(imm); CSR(imm) = t & ~src1; R(rd) = t);
  // csrrci

  // Catch-all handler
  INSTPAT("??????? ????? ????? ??? ????? ????? ??", inv    ,    N, INV(s->pc));

  INSTPAT_END();

  R(0) = 0; // reset $zero to 0

  return 0;
}

int isa_exec_once(Decode *s) {
  s->isa.inst.val = inst_fetch(&s->snpc, 4);
  return decode_exec(s);
}
