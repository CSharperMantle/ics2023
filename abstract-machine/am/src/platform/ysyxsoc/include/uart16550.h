#ifndef UART16550_H_INCLUDED_
#define UART16550_H_INCLUDED_

#include <stdint.h>

#define PERIP_UART16550_ADDR 0x10000000l

#define UART16550_REG_TXR 0l
#define UART16550_REG_IER 1l
#define UART16550_REG_LCR 3l
#define UART16550_REG_LSR 5l

#define UART16550_REG_DLL 0l
#define UART16550_REG_DLM 1l

#define UART16550_REG_DR1 8l
#define UART16550_REG_DR2 12l

typedef union Uart16550Lcr {
  struct {
    // Word length select
    uint8_t wls : 2;
    // Number of stop bits
    uint8_t stb : 1;
    // Parity enable
    uint8_t pen : 1;
    // Even parity select
    uint8_t eps : 1;
    // Stick parity
    uint8_t stick_parity : 1;
    // Set break
    uint8_t set_break : 1;
    // Divisor latch access bit
    uint8_t dlab : 1;
  };
  uint8_t as_byte;
} Uart16550Lcr_t;

typedef union Uart16550Ier {
  struct {
    // Enable received data available interrupt
    uint8_t erbfi : 1;
    // Enable transmitter holding register empty interrupt
    uint8_t etbei : 1;
    // Enable receiver line status interrupt
    uint8_t elsi : 1;
    // Enable modem status interrupt
    uint8_t edssi : 1;
    // Reserved
    uint8_t resv0 : 4;
  };
  uint8_t as_byte;
} Uart16550Ier_t;

typedef union Uart16550Fcr {
  struct {
    // Ignored 0
    uint8_t resv0 : 1;
    // Receiver FIFO reset
    uint8_t rcvr_fifo_rst : 1;
    // Transmitter FIFO reset
    uint8_t xmit_fifo_rst : 1;
    // Ignored 1
    uint8_t resv1 : 3;
    // Receiver FIFO trigger level
    uint8_t rcvr_fifo_trig_lvl : 2;
  };
  uint8_t as_byte;
} Uart16550Fcr_t;

typedef union Uart16550Lsr {
  struct {
    // Data ready
    uint8_t dr : 1;
    // Overrun error
    uint8_t oe : 1;
    // Parity error
    uint8_t pe : 1;
    // Framing error
    uint8_t fe : 1;
    // Break interrupt
    uint8_t bi : 1;
    // Transmitter holding register empty
    uint8_t thre : 1;
    // Transmitter empty
    uint8_t temt : 1;
    // Error in RCVR FIFO
    uint8_t e_rcvr_fifo : 1;
  };
  uint8_t as_byte;
} Uart16550Lsr_t;

#endif