#ifndef NPC_H_INCLUDED_
#define NPC_H_INCLUDED_

#include <klib-macros.h>
#include <riscv/riscv.h>

#define nemu_trap(code) asm volatile("mv a0, %0; ebreak" : : "r"(code))

#define RTC_ADDR  0xa0000048
#define UART_ADDR 0x10000000

#endif /* NPC_H_INCLUDED_ */