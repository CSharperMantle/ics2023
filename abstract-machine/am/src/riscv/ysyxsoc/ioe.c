#ifndef __ISA_RISCV32E__
// For clangd
#include "../../platform/ysyxsoc/include/ysyxsoc.h"
#else
#include <ysyxsoc.h>
#endif
#include <am.h>
#include <klib-macros.h>

static void am_uart_init(void) {
  Uart16550Lcr_t lcr;
  lcr = (Uart16550Lcr_t){
      .dlab = 1,
      .set_break = 0,
      .stick_parity = 0,
      .eps = 0,
      .pen = 0,
      .stb = 0,
      .wls = 0b11,
  };
  outb(PERIP_UART16550_ADDR + UART16550_REG_LCR, lcr.as_u8);
  outb(PERIP_UART16550_ADDR + UART16550_REG_DLM, 0x00);
  outb(PERIP_UART16550_ADDR + UART16550_REG_DLL, 0x01); // TODO: Make it concrete
  lcr = (Uart16550Lcr_t){
      .dlab = 0,
      .set_break = 0,
      .stick_parity = 0,
      .eps = 0,
      .pen = 0,
      .stb = 0,
      .wls = 0b11,
  };
  outb(PERIP_UART16550_ADDR + UART16550_REG_LCR, lcr.as_u8);
}

static void am_uart_config(AM_UART_CONFIG_T *cfg) {
  cfg->present = true;
}

static void am_uart_tx(AM_UART_TX_T *tx) {
  Uart16550Lsr_t lsr;
  do {
    lsr.as_u8 = inb(PERIP_UART16550_ADDR + UART16550_REG_LSR);
  } while (!lsr.thre);
  outb(PERIP_UART16550_ADDR + UART16550_REG_TXR, tx->data);
}

static void am_uart_rx(AM_UART_RX_T *rx) {
  Uart16550Lsr_t lsr;
  lsr.as_u8 = inb(PERIP_UART16550_ADDR + UART16550_REG_LSR);
  rx->data = lsr.dr ? inb(PERIP_UART16550_ADDR + UART16550_REG_RXR) : 0xff;
}

static void am_timer_init(void) {}

static void am_timer_config(AM_TIMER_CONFIG_T *cfg) {
  cfg->present = true;
  cfg->has_rtc = false;
}

static void am_timer_uptime(AM_TIMER_UPTIME_T *uptime) {
  const uint32_t cycles_l = inl(PERIP_CLINT_ADDR + CLINT_REG_MTIME_L);
  const uint32_t cycles_h = inl(PERIP_CLINT_ADDR + CLINT_REG_MTIME_H);
  uptime->us = (((uint64_t)cycles_h << 32) | ((uint64_t)cycles_l)) / 1000 * NS_PER_CYCLE;
}

static void am_input_config(AM_INPUT_CONFIG_T *cfg) {
  cfg->present = true;
}

static const int LOOKUP_SCANCODE_NORMAL[256] = {
    [0x0E] = AM_KEY_GRAVE,
    [0x16] = AM_KEY_1,
    [0x1E] = AM_KEY_2,
    [0x26] = AM_KEY_3,
    [0x25] = AM_KEY_4,
    [0x2E] = AM_KEY_5,
    [0x36] = AM_KEY_6,
    [0x3D] = AM_KEY_7,
    [0x3E] = AM_KEY_8,
    [0x46] = AM_KEY_9,
    [0x45] = AM_KEY_0,
    [0x4E] = AM_KEY_MINUS,
    [0x55] = AM_KEY_EQUALS,
    [0x1C] = AM_KEY_A,
    [0x32] = AM_KEY_B,
    [0x21] = AM_KEY_C,
    [0x23] = AM_KEY_D,
    [0x24] = AM_KEY_E,
    [0x2B] = AM_KEY_F,
    [0x34] = AM_KEY_G,
    [0x33] = AM_KEY_H,
    [0x43] = AM_KEY_I,
    [0x3B] = AM_KEY_J,
    [0x42] = AM_KEY_K,
    [0x4B] = AM_KEY_L,
    [0x3A] = AM_KEY_M,
    [0x31] = AM_KEY_N,
    [0x44] = AM_KEY_O,
    [0x4D] = AM_KEY_P,
    [0x15] = AM_KEY_Q,
    [0x2D] = AM_KEY_R,
    [0x1B] = AM_KEY_S,
    [0x2C] = AM_KEY_T,
    [0x3C] = AM_KEY_U,
    [0x2A] = AM_KEY_V,
    [0x1D] = AM_KEY_W,
    [0x22] = AM_KEY_X,
    [0x35] = AM_KEY_Y,
    [0x1A] = AM_KEY_Z,
    [0x54] = AM_KEY_LEFTBRACKET,
    [0x5B] = AM_KEY_RIGHTBRACKET,
    [0x5D] = AM_KEY_BACKSLASH,
    [0x4C] = AM_KEY_SEMICOLON,
    [0x52] = AM_KEY_APOSTROPHE,
    [0x5A] = AM_KEY_RETURN,
    [0x41] = AM_KEY_COMMA,
    [0x49] = AM_KEY_PERIOD,
    [0x4A] = AM_KEY_SLASH,
    [0x66] = AM_KEY_BACKSPACE,
    [0x0D] = AM_KEY_TAB,
    [0x58] = AM_KEY_CAPSLOCK,
    [0x12] = AM_KEY_LSHIFT,
    [0x14] = AM_KEY_LCTRL,
    [0x11] = AM_KEY_LALT,
    [0x29] = AM_KEY_SPACE,
    [0x59] = AM_KEY_RSHIFT,
    [0x76] = AM_KEY_ESCAPE,
    [0x05] = AM_KEY_F1,
    [0x06] = AM_KEY_F2,
    [0x04] = AM_KEY_F3,
    [0x0C] = AM_KEY_F4,
    [0x03] = AM_KEY_F5,
    [0x0B] = AM_KEY_F6,
    [0x83] = AM_KEY_F7,
    [0x0A] = AM_KEY_F8,
    [0x01] = AM_KEY_F9,
    [0x09] = AM_KEY_F10,
    [0x78] = AM_KEY_F11,
    [0x07] = AM_KEY_F12,
};

static const int LOOKUP_SCANCODE_EXTEND[256] = {
    [0x11] = AM_KEY_RALT,
    [0x14] = AM_KEY_RCTRL,
    [0x71] = AM_KEY_DELETE,
    [0x69] = AM_KEY_END,
    [0x6C] = AM_KEY_HOME,
    [0x70] = AM_KEY_INSERT,
    [0x7A] = AM_KEY_PAGEDOWN,
    [0x7D] = AM_KEY_PAGEUP,
    [0x72] = AM_KEY_DOWN,
    [0x6B] = AM_KEY_LEFT,
    [0x74] = AM_KEY_RIGHT,
    [0x75] = AM_KEY_UP,
};

static void am_input_keybrd(AM_INPUT_KEYBRD_T *kbd) {

  static bool is_break = false;
  static bool is_extend = false;
  const uint8_t scancode = inb(PERIP_PS2_KBD_ADDR + PS2_KBD_REG_SCANCODE);

  kbd->keydown = false;
  kbd->keycode = AM_KEY_NONE;

  if (scancode == 0xe0) {
    is_extend = true;
  } else if (scancode == 0xf0) {
    is_break = true;
  } else if (scancode != 0x0) {
    kbd->keydown = !is_break;
    kbd->keycode = is_extend ? LOOKUP_SCANCODE_EXTEND[scancode] : LOOKUP_SCANCODE_NORMAL[scancode];
    is_extend = false;
    is_break = false;
  }
}

static void am_gpu_config(AM_GPU_CONFIG_T *cfg) {
  cfg->present = false;
}

typedef void (*handler_t)(void *);
static void *regs[128] = {
    [AM_UART_CONFIG] = am_uart_config,
    [AM_UART_TX] = am_uart_tx,
    [AM_UART_RX] = am_uart_rx,
    [AM_TIMER_CONFIG] = am_timer_config,
    [AM_TIMER_UPTIME] = am_timer_uptime,
    [AM_INPUT_CONFIG] = am_input_config,
    [AM_INPUT_KEYBRD] = am_input_keybrd,
    [AM_GPU_CONFIG] = am_gpu_config,
};

static void no_reg(void *buf) {
  panic("access nonexist register");
}

bool ioe_init(void) {
  static bool inited = false;

  if (inited) {
    return true;
  }

  am_uart_init();
  am_timer_init();

  for (int i = 0; i < LENGTH(regs); i++) {
    if (regs[i] == NULL) {
      regs[i] = no_reg;
    }
  }

  inited = true;
  return true;
}

void ioe_read(int reg, void *buf) {
  ((handler_t)regs[reg])(buf);
}

void ioe_write(int reg, void *buf) {
  ((handler_t)regs[reg])(buf);
}
