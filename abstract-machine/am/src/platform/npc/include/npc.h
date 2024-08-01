#ifndef NPC_H_INCLUDED_
#define NPC_H_INCLUDED_

#include <klib-macros.h>
#include <riscv/riscv.h>

#define nemu_trap(code) asm volatile("mv a0, %0; ebreak" : : "r"(code))

#define NS_PER_CYCLE 200

#define CLINT_ADDR 0x02000000
#define UART_ADDR  0x10000000

#endif /* NPC_H_INCLUDED_ */