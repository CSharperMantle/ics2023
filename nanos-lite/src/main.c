#include <common.h>
#include <device.h>
#include <fs.h>
#include <proc.h>
#include <ramdisk.h>
#include <memory.h>

void init_irq(void);

int main() {
  extern const char logo[];
  printf("%s", logo);
  Log("Build time: %s, %s", __TIME__, __DATE__);

  init_mm();

  init_device();

  init_ramdisk();

#ifdef HAS_CTE
  init_irq();
#endif

  init_fs();

  init_proc();

  Log("Finish initialization");

#ifdef HAS_CTE
  yield();
#endif

  panic("Should not reach here");
}
