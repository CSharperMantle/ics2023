#include <am.h>
#include <nemu.h>
#include <stdint.h>

#define KEYDOWN_MASK 0x8000

#define KBD_ADDR_REG_KEY (KBD_ADDR + 0)

void __am_input_keybrd(AM_INPUT_KEYBRD_T *kbd) {
  uint32_t key = inl(KBD_ADDR_REG_KEY);

  kbd->keydown = key & KEYDOWN_MASK;
  kbd->keycode = key & ~KEYDOWN_MASK;
}
