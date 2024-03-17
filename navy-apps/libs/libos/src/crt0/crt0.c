#include <assert.h>
#include <stdint.h>
#include <stdlib.h>

extern void __libc_init_array(void);
extern void __libc_fini_array(void);

extern int main(int argc, char *argv[], char *envp[]);
extern char **environ;

void call_main(uintptr_t *args) {
  char *empty[] = {NULL};
  environ = empty;

  atexit(__libc_fini_array);

  __libc_init_array();
  exit(main(0, empty, empty));

  assert(("unreachable", 0));
}
