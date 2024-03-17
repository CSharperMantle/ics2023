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
  const uint32_t *const pixels = (uint32_t *)ctl->pixels;
  uint32_t *const fb = (uint32_t *)FB_ADDR;
  for (int y = 0; y < req_h; y++) {
    uint32_t *const fb_row = &fb[(req_y + y) * scr_w];
    const uint32_t *const pixels_row = &pixels[y * req_w];
    for (int x = 0; x < req_w; x++) {
      outl((uintptr_t)&fb_row[req_x + x], pixels_row[x]);
    }
  }
  if (ctl->sync) {
    outl(VGACTL_ADDR_REG_SYNC, 1);
  }
}

void __am_gpu_memcpy(AM_GPU_MEMCPY_T *params) {
  const uintptr_t src = (uintptr_t)params->src;
  const uintptr_t dst = (uintptr_t)(FB_ADDR + params->dest);
  size_t leftover = params->size & 0b11u;
  for (size_t i = 0; i < (size_t)params->size >> 2; i++) {
    outl(dst + i * 4, ((uint32_t *)src)[i]);
  }
  for (size_t i = leftover; i < (size_t)params->size; i++) {
    outb(dst + i, ((uint8_t *)src)[i]);
  }
}

void __am_gpu_status(AM_GPU_STATUS_T *status) {
  status->ready = true;
}
