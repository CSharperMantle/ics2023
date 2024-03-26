#include <elf.h>
#include <fcntl.h>
#include <fs.h>
#include <proc.h>

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

#define HAS_MAG_(e_, i_) (e_[EI_MAG##i_] == ELFMAG##i_)

static uintptr_t loader(PCB *pcb, const char *filename) {
  int f = fs_open(filename, O_RDONLY, 0);
  assert(f >= 0);

  Elf_Ehdr ehdr;
  fs_read(f, &ehdr, sizeof(ehdr));
  assert(HAS_MAG_(ehdr.e_ident, 0) && HAS_MAG_(ehdr.e_ident, 1) && HAS_MAG_(ehdr.e_ident, 2)
         && HAS_MAG_(ehdr.e_ident, 3));
  assert(ehdr.e_machine == ELF_MACHINE_TYPE);

  Elf_Phdr phdr;
  assert(ehdr.e_phentsize == sizeof(phdr));

  for (size_t i = 0; i < ehdr.e_phnum; i++) {
    fs_lseek(f, ehdr.e_phoff + sizeof(phdr) * i, SEEK_SET);
    fs_read(f, &phdr, sizeof(phdr));
    if (phdr.p_type != PT_LOAD) {
      continue;
    }
    fs_lseek(f, phdr.p_offset, SEEK_SET);
    fs_read(f, (void *)phdr.p_vaddr, phdr.p_filesz);
    memset((void *)(phdr.p_vaddr + phdr.p_filesz), 0, phdr.p_memsz - phdr.p_filesz);
  }

  fs_close(f);

  return ehdr.e_entry;
}

void naive_uload(PCB *pcb, const char *filename) {
  uintptr_t entry = loader(pcb, filename);
  Log("Jump to entry = %p", entry);
  ((void (*)())entry)();
}

void context_kload(PCB *pcb, void (*entry)(void *), void *arg) {
  const Area stack = (Area){.start = pcb->stack, .end = (void *)pcb->stack + sizeof(pcb->stack)};
  pcb->cp = kcontext(stack, entry, arg);
}
