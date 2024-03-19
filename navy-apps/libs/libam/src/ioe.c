#include <am.h>

bool ioe_init(void) {
  return true;
}

void ioe_read(int reg, void *buf) {
  int fioe = open("/dev/ioe_pt", O_RDONLY);
  lseek(fioe, reg, SEEK_SET);
  read(fioe, buf, -1);
  close(fioe);
}

void ioe_write(int reg, void *buf) {
  int fioe = open("/dev/ioe_pt", O_WRONLY);
  lseek(fioe, reg, SEEK_SET);
  write(fioe, buf, -1);
  close(fioe);
}
