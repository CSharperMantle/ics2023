#include <cpu/btrace.h>
#include <isa.h>
#include <stdbool.h>
#include <stdio.h>

FILE *file_btrace = NULL;

#ifdef CONFIG_BTRACE

void init_btrace(const char *path) {
  if (file_btrace != NULL) {
    fclose(file_btrace);
    file_btrace = NULL;
  }
  if (path != NULL) {
    file_btrace = fopen(path, "wb");
  }
}

void write_btrace(word_t pc, bool taken) {
  if (file_btrace != NULL) {
    const BTraceEntry_t entry = {
        .pc.as_word_t = pc,
        .is_branch = true,
        .taken = taken,
    };
    fwrite(&entry, sizeof entry, 1, file_btrace);
  }
}

void flush_btrace(void) {
  if (file_btrace != NULL) {
    fflush(file_btrace);
  }
}

void close_btrace(void) {
  if (file_btrace != NULL) {
    fclose(file_btrace);
  }
}

#else

void init_btrace(const char *path) {}

void write_btrace(word_t pc, bool taken) {}

void flush_btrace(void) {}

void close_btrace(void) {}

#endif
