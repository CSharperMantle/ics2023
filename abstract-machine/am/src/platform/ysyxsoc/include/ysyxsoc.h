#ifndef YSYXSOC_H_INCLUDED_
#define YSYXSOC_H_INCLUDED_

#include <klib-macros.h>
#include <riscv/riscv.h>

#include "clint.h"
#include "spi.h"
#include "uart16550.h"

#define nemu_trap(code) asm volatile("mv a0, %0; ebreak" : : "r"(code))

#define PERIP_SRAM_ADDR  0x0f000000l
#define PERIP_FLASH_ADDR 0x30000000l

#endif /* YSYXSOC_H_INCLUDED_ */
