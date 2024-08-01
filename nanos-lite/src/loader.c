#include <elf.h>
#include <fcntl.h>
#include <fs.h>
#include <memory.h>
#include <proc.h>
#include <string.h>

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
#elif defined(__ISA_RISCV32__) || defined(__ISA_RISCV64__) || defined(__ISA_RISCV32E__)
#define ELF_MACHINE_TYPE EM_RISCV
#else
#error Unsupported ISA
#endif

#define HAS_MAG_(e_, i_) (e_[EI_MAG##i_] == ELFMAG##i_)

static uintptr_t calc_aligned_pages(uintptr_t start, size_t len, size_t *out_n) {
  const uintptr_t start_aligned = start & ~(uintptr_t)(PGSIZE - 1);
  const uintptr_t end_aligned = (start + len) + (((start + len) & (PGSIZE - 1)) == 0 ? 0 : PGSIZE);
  *out_n = (end_aligned - start_aligned) / PGSIZE;
  return start_aligned;
}

static uintptr_t loader(PCB *pcb, const char *filename) {
  int f = fs_open(filename, O_RDONLY, 0);
  assert(f >= 0);

  Log("loading \"%s\"", filename);

  Elf_Ehdr ehdr;
  fs_read(f, &ehdr, sizeof(ehdr));
  assert(HAS_MAG_(ehdr.e_ident, 0) && HAS_MAG_(ehdr.e_ident, 1) && HAS_MAG_(ehdr.e_ident, 2)
         && HAS_MAG_(ehdr.e_ident, 3));
  assert(ehdr.e_machine == ELF_MACHINE_TYPE);

  Elf_Phdr phdr;
  assert(ehdr.e_phentsize == sizeof(phdr));

  uintptr_t max_end = 0;

  for (size_t i = 0; i < ehdr.e_phnum; i++) {
    fs_lseek(f, ehdr.e_phoff + sizeof(phdr) * i, SEEK_SET);
    fs_read(f, &phdr, sizeof(phdr));
    if (phdr.p_type != PT_LOAD) {
      continue;
    }

    size_t n_pages;
    const uintptr_t start_aligned = calc_aligned_pages(phdr.p_vaddr, phdr.p_memsz, &n_pages);
    void *const pages = new_page(n_pages);
    void *const pages_start = pages + (phdr.p_vaddr - start_aligned);
    assert(n_pages * PGSIZE >= phdr.p_memsz);

    fs_lseek(f, phdr.p_offset, SEEK_SET);
    fs_read(f, pages_start, phdr.p_filesz);
    memset(pages_start + phdr.p_filesz, 0, phdr.p_memsz - phdr.p_filesz);

    if (phdr.p_vaddr + phdr.p_memsz > max_end) {
      max_end = phdr.p_vaddr + phdr.p_memsz;
    }

    for (size_t j = 0; j < n_pages; j++) {
      map(&pcb->as,
          (void *)start_aligned + PGSIZE * j,
          pages + PGSIZE * j,
          PTE_R | PTE_W | PTE_X | PTE_U);
    }
  }

  fs_close(f);

  pcb->max_brk = (max_end % PGSIZE == 0) ? max_end : (max_end / PGSIZE + 1) * PGSIZE;

  return ehdr.e_entry;
}

void naive_uload(PCB *pcb, const char *filename) {
  uintptr_t entry = loader(pcb, filename);
  Log("Jump to entry = %p", (void *)entry);
  ((void (*)())entry)();
}

void context_kload(PCB *pcb, void (*entry)(void *), void *arg) {
  const Area kstack = (Area){.start = pcb->stack, .end = (void *)pcb->stack + sizeof(pcb->stack)};
  pcb->cp = kcontext(kstack, entry, arg);

  Log("context: %p (p=%d, a0=%p, sp=%p)",
      pcb->cp,
      pcb->cp->np,
      (void *)pcb->cp->GPRx,
      (void *)pcb->cp->gpr[2]);
}

static size_t len_varargs(char *const varargs[]) {
  if (varargs == NULL) {
    return 0;
  }

  Log("scanning varargs list [%p]", varargs);
  size_t len = 0;
  while (varargs[len] != NULL) {
    Log("+0x%x: %p -> %p -> %s", sizeof(char *) * len, &varargs[len], varargs[len], varargs[len]);
    len++;
  }
  Log("+0x%x: %p -> %p", sizeof(char *) * len, &varargs[len], varargs[len]);
  return len;
}

static size_t get_varargs_size(char *const varargs[], size_t len, size_t sizes[]) {
  if (varargs == NULL) {
    return 0;
  }

  size_t sz_str = 0;
  for (size_t i = 0; i < len; i++) {
    const size_t sz = strlen(varargs[i]) + 1;
    sz_str += sz;
    sizes[i] = sz;
  }
  return sz_str;
}

void context_uload(PCB *pcb, const char *filename, char *const argv[], char *const envp[]) {
  const Area kstack = (Area){.start = pcb->stack, .end = (void *)pcb->stack + sizeof(pcb->stack)};

  const int argc = (int)len_varargs(argv);
  size_t argv_sizes[argc];
  const size_t size_args = get_varargs_size(argv, argc, argv_sizes);

  const size_t envc = len_varargs(envp);
  size_t envp_sizes[envc];
  const size_t size_envs = get_varargs_size(envp, envc, envp_sizes);

  void *const ustack_start = new_page(8);
  void *const ustack_end = ustack_start + PGSIZE * 8;

  void *new_sp = ustack_end;

  char *envp_table[envc + 1];
  envp_table[envc] = NULL;
  for (size_t i = 0; i < envc; i++) {
    new_sp -= envp_sizes[i];
    Log("copying envp %p <~ %p -> %s", new_sp, envp[i], envp[i]);
    memcpy(new_sp, envp[i], envp_sizes[i]);
    envp_table[i] = new_sp;
  }

  char *argv_table[argc + 1];
  argv_table[argc] = NULL;
  for (size_t i = 0; i < argc; i++) {
    new_sp -= argv_sizes[i];
    Log("copying argv %p <~ %p -> %s", new_sp, argv[i], argv[i]);
    memcpy(new_sp, argv[i], argv_sizes[i]);
    argv_table[i] = new_sp;
  }

  assert(ustack_end - new_sp == size_args + size_envs);

  for (size_t i = envc + 1; i > 0; i--) {
    new_sp -= sizeof(char *);
    *(char **)new_sp = envp_table[i - 1];
    Log("envp set: %p -> %p -> %s",
        new_sp,
        *(char **)new_sp,
        *(char **)new_sp == NULL ? "(null)" : *(char **)new_sp);
  }

  for (size_t i = argc + 1; i > 0; i--) {
    new_sp -= sizeof(char *);
    *(char **)new_sp = argv_table[i - 1];
    Log("argv set: %p -> %p -> %s",
        new_sp,
        *(char **)new_sp,
        *(char **)new_sp == NULL ? "(null)" : *(char **)new_sp);
  }

  new_sp -= sizeof(int);
  *(int *)new_sp = (int)argc;
  Log("argc set: %p -> 0x%x", new_sp, *(int *)new_sp);

  protect(&pcb->as);

  const uintptr_t entry = loader(pcb, filename);
  Context *const ctx = ucontext(&pcb->as, kstack, (void *)entry);

  for (size_t i = 8; i > 0; i--) {
    map(&pcb->as, pcb->as.area.end - PGSIZE * i, ustack_end - PGSIZE * i, PTE_R | PTE_W | PTE_U);
  }
  ctx->gpr[2] = (uintptr_t)(pcb->as.area.end - (ustack_end - new_sp));

  pcb->cp = ctx;

  Log("context: %p (p=%d, a0=%p, sp=%p)",
      pcb->cp,
      pcb->cp->np,
      (void *)pcb->cp->GPRx,
      (void *)pcb->cp->gpr[2]);
}
