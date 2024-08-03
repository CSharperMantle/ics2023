#ifndef YSYXSOC_H_INCLUDED_
#define YSYXSOC_H_INCLUDED_

#include <klib-macros.h>
#include <riscv/riscv.h>

#define nemu_trap(code) asm volatile("mv a0, %0; ebreak" : : "r"(code))

#define PERIP_CLINT_ADDR     0x02000000l
#define PERIP_SRAM_ADDR      0x0f000000l
#define PERIP_UART16550_ADDR 0x10000000l

#endif /* YSYXSOC_H_INCLUDED_ */
