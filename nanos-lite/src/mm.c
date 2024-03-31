#include <memory.h>

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
  return 0;
}

void init_mm(void) {
  pf = (void *)ROUNDUP(heap.start, PGSIZE);
  Log("free physical pages starting from %p", pf);

#ifdef HAS_VME
  vme_init(pg_alloc, free_page);
#endif
}
