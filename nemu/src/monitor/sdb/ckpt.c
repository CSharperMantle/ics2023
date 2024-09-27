#include "ckpt.h"
#include <common.h>
#include <cpu/cpu.h>
#include <isa.h>
#include <memory/paddr.h>
#include <memory/vaddr.h>
#include <stdio.h>

#ifndef CONFIG_TARGET_SHARE

#if defined(CONFIG_PMEM_MALLOC)
#define PMEM_SIZE (CONFIG_MSIZE)
#else // CONFIG_PMEM_GARRAY
#define PMEM_SIZE (sizeof(pmem))
#endif

#define CHUNK_SIZE 8192

#endif

int ckpt_load_from(const char *filename) {
#ifdef CONFIG_TARGET_SHARE
  puts("no checkpoint support in REF mode");
  return -1;
#else
  FILE *f = fopen(filename, "rb");
  if (f == NULL) {
    return -1;
  }
  setvbuf(f, NULL, _IONBF, 0);

  HwState_t hw_state;
  fread(&hw_state, sizeof(hw_state), 1, f);
  cpu = hw_state.cpu;

  fread(pmem, sizeof(uint8_t), PMEM_SIZE / sizeof(uint8_t), f);

  const int err = ferror(f);
  fclose(f);
  if (err != 0) {
    printf("ferror() non-zero: %d: %s", err, strerror(err));
    return -1;
  } else {
    return 0;
  }
#endif
}

int ckpt_save_to(const char *filename) {
#ifdef CONFIG_TARGET_SHARE
  puts("no checkpoint support in REF mode");
  return -1;
#else
  FILE *f = fopen(filename, "wb");
  if (f == NULL) {
    return -1;
  }
  setvbuf(f, NULL, _IONBF, 0);

  const HwState_t hw_state = {
      .cpu = cpu,
  };
  fwrite(&hw_state, sizeof(hw_state), 1, f);

  const size_t n_chunks = PMEM_SIZE / CHUNK_SIZE;
  const size_t leftover = PMEM_SIZE % CHUNK_SIZE;
  Log("%zu chunks; %zu leftover bytes", n_chunks, leftover);
  for (size_t i = 0; i < n_chunks; i++) {
    fwrite(&pmem[CHUNK_SIZE * i], sizeof(uint8_t), CHUNK_SIZE, f);
  }
  if (leftover != 0) {
    fwrite(&pmem[n_chunks * CHUNK_SIZE], sizeof(uint8_t), leftover, f);
  }

  const int err = ferror(f);
  fclose(f);
  if (err != 0) {
    printf("ferror() non-zero: %d: %s", err, strerror(err));
    return -1;
  } else {
    return 0;
  }
#endif
}
