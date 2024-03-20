#include <NDL.h>
#include <stdbool.h>
#include <stdio.h>
#include <sys/time.h>

static bool tick = true;

int main(void) {
  NDL_Init(0);

  uint32_t start, now;
  while (1) {
    start = NDL_GetTicks();
    do {
      now = NDL_GetTicks();
    } while (now < start + 500);
    puts(tick ? "Tick" : "Tock");
    tick = !tick;
  }

  NDL_Quit();
}