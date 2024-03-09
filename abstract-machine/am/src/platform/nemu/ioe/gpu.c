#include <am.h>
#include <nemu.h>
#include <stdint.h>

#define VGACTL_ADDR_REG_WH   (VGACTL_ADDR + 0)
#define VGACTL_ADDR_REG_SYNC (VGACTL_ADDR + 4)

void __am_gpu_init(void) {}

void __am_gpu_config(AM_GPU_CONFIG_T *cfg) {
  const uint32_t wh = inl(VGACTL_ADDR_REG_WH);

  const uint32_t w = wh >> 16;
  const uint32_t h = wh & 0xFFFF;

  *cfg = (AM_GPU_CONFIG_T){
      .present = true,
      .has_accel = false,
      .width = w,
      .height = h,
      .vmemsz = w * h * sizeof(uint32_t),
  };
}

void __am_gpu_fbdraw(AM_GPU_FBDRAW_T *ctl) {
  const uint32_t scr_w = inl(VGACTL_ADDR_REG_WH) >> 16;
  const int req_x = ctl->x;
  const int req_y = ctl->y;
  const int req_w = ctl->w;
  const int req_h = ctl->h;
  const uint32_t *pixels = (uint32_t *)ctl->pixels;
  uint32_t *const fb = (uint32_t *)FB_ADDR;
  for (int y = 0; y < req_h; y++) {
    for (int x = 0; x < req_w; x++) {
      const uint32_t pixel = pixels[y * req_w + x];
      outl((uintptr_t)&fb[(req_y + y) * scr_w + (req_x + x)], pixel);
    }
  }
  if (ctl->sync) {
    outl(VGACTL_ADDR_REG_SYNC, 1);
  }
}

void __am_gpu_status(AM_GPU_STATUS_T *status) {
  status->ready = true;
}
