#ifndef SPI_H_INCLUDED_
#define SPI_H_INCLUDED_

#include <stdint.h>

#define PERIP_SPI_MAS_ADDR 0x10001000l

#define SPI_MAS_REG_RX0  0x00l
#define SPI_MAS_REG_RX1  0x04l
#define SPI_MAS_REG_RX2  0x08l
#define SPI_MAS_REG_RX3  0x0cl
#define SPI_MAS_REG_TX0  0x00l
#define SPI_MAS_REG_TX1  0x04l
#define SPI_MAS_REG_TX2  0x08l
#define SPI_MAS_REG_TX3  0x0cl
#define SPI_MAS_REG_CTRL 0x10l
#define SPI_MAS_REG_DIV  0x14l
#define SPI_MAS_REG_SS   0x18l

typedef union SpiMasCtrl {
    struct {
        uint32_t char_len : 7;
        uint32_t resv0 : 1;
        uint32_t go_bsy : 1;
        uint32_t rx_neg : 1;
        uint32_t tx_neg : 1;
        uint32_t lsb : 1;
        uint32_t ie : 1;
        uint32_t ass : 1;
        uint32_t resv1 : 18;
    };
    uint32_t as_u32;
} SpiMasCtrl_t;

#endif /* SPI_H_INCLUDED_ */