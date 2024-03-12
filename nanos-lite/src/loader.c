#include <elf.h>
#include <proc.h>
#include <ramdisk.h>

#ifdef __LP64__
#define Elf_Ehdr Elf64_Ehdr
#define Elf_Phdr Elf64_Phdr
#else
#define Elf_Ehdr Elf32_Ehdr
#define Elf_Phdr Elf32_Phdr
#endif

#if defined(__ISA_AM_NATIVE__)
#define ELF_MACHINE_TYPE EM_X86_64
#elif defined(__ISA_X86__)
#define ELF_MACHINE_TYPE EM_X86_64
#elif defined(__ISA_MIPS32__)
#define ELF_MACHINE_TYPE EM_MIPS
#elif defined(__ISA_RISCV32__) || defined(__ISA_RISCV64__)
#define ELF_MACHINE_TYPE EM_RISCV
#else
#error Unsupported ISA
#endif

#define ARRLEN(arr_) (sizeof(arr_) / sizeof(arr_[0]))

#define HAS_MAG_(e_, i_) (e_[EI_MAG##i_] == ELFMAG##i_)

static uintptr_t loader(PCB *pcb, const char *filename) {
  Elf_Ehdr ehdr;
  ramdisk_read(&ehdr, 0, sizeof(ehdr));
  assert(HAS_MAG_(ehdr.e_ident, 0) && HAS_MAG_(ehdr.e_ident, 1) && HAS_MAG_(ehdr.e_ident, 2)
         && HAS_MAG_(ehdr.e_ident, 3));
  assert(ehdr.e_machine == ELF_MACHINE_TYPE);

  Elf_Phdr phdr;
  assert(ehdr.e_phentsize == sizeof(phdr));

  for (size_t i = 0; i < ehdr.e_phnum; i++) {
    ramdisk_read(&phdr, ehdr.e_phoff + sizeof(phdr) * i, sizeof(phdr));
    if (phdr.p_type != PT_LOAD) {
      continue;
    }
    ramdisk_read((void *)phdr.p_vaddr, phdr.p_offset, phdr.p_filesz);
    memset((void *)(phdr.p_vaddr + phdr.p_memsz), 0, phdr.p_memsz - phdr.p_filesz);
  }

  return ehdr.e_entry;
}

void naive_uload(PCB *pcb, const char *filename) {
  uintptr_t entry = loader(pcb, filename);
  Log("Jump to entry = %p", entry);
  ((void (*)())entry)();
}
