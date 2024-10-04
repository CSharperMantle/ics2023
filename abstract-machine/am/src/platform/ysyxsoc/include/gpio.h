#ifndef GPIO_H_INCLUDED_
#define GPIO_H_INCLUDED_

#include <stdint.h>

#define PERIP_GPIO_CTRL_ADDR 0x10002000l

#define GPIO_CTRL_REG_DOUT 0x00l
#define GPIO_CTRL_REG_DIN  0x04l
#define GPIO_CTRL_REG_SEGS 0x08l

#endif /* GPIO_H_INCLUDED_ */