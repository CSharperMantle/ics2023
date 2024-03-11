#include "sdb.h"
#include <elf.h>
#include <stdio.h>
#include <stdlib.h>

#ifdef CONFIG_FTRACE

#define HAS_MAG_(e_, i_) (e_[EI_MAG##i_] == ELFMAG##i_)

static void seek_read(FILE *f, void *buf, long off, size_t size, size_t n) {
  fseek(f, off, SEEK_SET);
  fread(buf, size, n, f);
}

bool has_elf = false;
SdbElf_t elf;
int64_t ftrace_call_level = 0;

int elf_read(FILE *felf) {
  if (elf.symtab) {
    free(elf.symtab);
    elf.symtab = NULL;
  }
  if (elf.strtab) {
    free(elf.strtab);
    elf.strtab = NULL;
  }
  if (elf.funcs) {
    free(elf.funcs);
    elf.funcs = NULL;
  }

  seek_read(felf, &elf.ehdr, 0, sizeof(Elf64_Ehdr), 1);
  uint8_t *e_ident = elf.ehdr.e_ident;
  if (!(HAS_MAG_(e_ident, 0) && HAS_MAG_(e_ident, 1) && HAS_MAG_(e_ident, 2)
        && HAS_MAG_(e_ident, 3))) {
    printf("Bad ELF magic: found %02hhx %02hhx %02hhx %02hhx\n",
           e_ident[0],
           e_ident[1],
           e_ident[2],
           e_ident[3]);
    return 1;
  }

  assert(sizeof(Elf64_Shdr) == elf.ehdr.e_shentsize);
  Elf64_Shdr shdr;
  bool has_symtab = false;
  bool has_strtab = false;
  for (size_t i = 0; i < elf.ehdr.e_shnum; i++) {
    seek_read(felf, &shdr, elf.ehdr.e_shoff + i * sizeof(Elf64_Shdr), sizeof(Elf64_Shdr), 1);
    if (!has_symtab && shdr.sh_type == SHT_SYMTAB) {
      assert(shdr.sh_entsize == sizeof(Elf64_Sym));
      elf.symtab = malloc(shdr.sh_size);
      assert(elf.symtab != NULL);
      seek_read(felf, elf.symtab, shdr.sh_offset, shdr.sh_size, 1);
      elf.nent_symtab = shdr.sh_size / sizeof(Elf64_Sym);
      has_symtab = true;
    } else if (!has_strtab && shdr.sh_type == SHT_STRTAB) {
      elf.strtab = malloc(shdr.sh_size);
      assert(elf.strtab != NULL);
      seek_read(felf, elf.strtab, shdr.sh_offset, shdr.sh_size, 1);
      has_strtab = true;
    }
  }
  if (!has_symtab) {
    puts("(No .symtab section found in ELF)");
    return 0;
  }
  if (!has_strtab) {
    puts("(No .strtab section found in ELF)");
    return 0;
  }

  size_t nent_funcs = 0;
  for (size_t i = 0; i < elf.nent_symtab; i++) {
    if (ELF64_ST_TYPE(elf.symtab[i].st_info) == STT_FUNC) {
      nent_funcs++;
    }
  }

  elf.funcs = malloc(sizeof(SdbElfFunc_t) * nent_funcs);
  assert(elf.funcs != NULL);
  elf.nent_funcs = nent_funcs;
  size_t j = 0;
  for (size_t i = 0; i < elf.nent_symtab; i++) {
    Elf64_Sym sym_ent = elf.symtab[i];
    if (ELF64_ST_TYPE(sym_ent.st_info) == STT_FUNC) {
      elf.funcs[j].name = elf.strtab + sym_ent.st_name;
      elf.funcs[j].start = sym_ent.st_value;
      elf.funcs[j].end = sym_ent.st_value + sym_ent.st_size;
      Log("found func %s (0x%016lx-0x%016lx)",
          elf.funcs[j].name,
          elf.funcs[j].start,
          elf.funcs[j].end);
      j++;
    }
  }

  elf.valid = true;

  ftrace_call_level = 0;
  return 0;
}

char *elf_get_func_name(uint64_t addr, bool *at_start) {
  if (!elf.valid) {
    return "??";
  }
  for (size_t i = 0; i < elf.nent_funcs; i++) {
    SdbElfFunc_t func = elf.funcs[i];
    if (func.start <= addr && addr < func.end) {
      if (at_start != NULL) {
        *at_start = addr == func.start;
      }
      return func.name;
    }
  }
  return "??";
}

#endif
