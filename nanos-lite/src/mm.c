#include <memory.h>
#include <proc.h>

static void *pf = NULL;

void *new_page(size_t nr_page) {
  void *const old_pf = pf;
  const size_t delta = nr_page * sizeof(uint8_t) * PGSIZE;
  assert(pf + delta <= heap.end);
  pf += delta;
  return old_pf;
}

#ifdef HAS_VME
static void *pg_alloc(int n) {
  void *const page = new_page(n / PGSIZE);
  assert(page != NULL);
  memset(page, 0, n);
  return page;
}
#endif

void free_page(void *p) {
  panic("not implement yet");
}

/* The brk() system call handler. */
int mm_brk(uintptr_t brk) {
  if (current->max_brk == 0) {
    current->max_brk = (brk % PGSIZE == 0) ? brk : (brk / PGSIZE + 1) * PGSIZE;
  } else if (brk > current->max_brk) {
    const uintptr_t new_size = brk - current->max_brk;
    const size_t n_pages = new_size / PGSIZE + (new_size % PGSIZE != 0 ? 1 : 0);
    const uintptr_t new_brk = current->max_brk + PGSIZE * n_pages;
    void *const pages = new_page(n_pages);
    for (size_t i = 0; i < n_pages; i++) {
      map(&current->as,
          (void *)current->max_brk + i * PGSIZE,
          pages + i * PGSIZE,
          PTE_R | PTE_W | PTE_U /* TODO: U or not? */);
    }
    current->max_brk = new_brk;
  }
  return 0;
}

void init_mm(void) {
  pf = (void *)ROUNDUP(heap.start, PGSIZE);
  Log("free physical pages starting from %p", pf);

#ifdef HAS_VME
  vme_init(pg_alloc, free_page);
#endif
}
