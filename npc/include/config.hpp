#ifndef NPC_CONFIG_HPP_
#define NPC_CONFIG_HPP_

#define CONFIG_DUMP_WAVE 0

#define CONFIG_RT_CHECK
// #define CONFIG_MTRACE
// #define CONFIG_DTRACE
#define CONFIG_SIM_STUCK_DETECT_THRESHOLD 1000
// #define CONFIG_ISA64
#define CONFIG_IRINGBUF_NR_ELEM 32

#define CONFIG_DEVICE
#define CONFIG_RTC_MMIO    0xa0000048
#define CONFIG_SERIAL_MMIO 0xa00003f8

#endif /* NPC_CONFIG_HPP_ */