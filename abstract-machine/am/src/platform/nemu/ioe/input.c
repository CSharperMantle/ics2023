#include <am.h>
#include <nemu.h>

#define KEYDOWN_MASK 0x8000

static volatile uint32_t *const KEYBOARD_PORT_BASE = (volatile uint32_t *)0xa0000060;

void __am_input_keybrd(AM_INPUT_KEYBRD_T *kbd) {
  uint32_t key = KEYBOARD_PORT_BASE[0];

  kbd->keydown = key & KEYDOWN_MASK;
  kbd->keycode = key & ~KEYDOWN_MASK;
}
