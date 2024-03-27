#include <assert.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

extern void __libc_init_array(void);
extern void __libc_fini_array(void);

extern int main(int argc, char *argv[], char *envp[]);
extern char **environ;

void call_main(void *a0) {
  assert(a0 != NULL);

  void *p_args = a0;

  const int argc = *(int *)p_args;
  p_args += sizeof(int);

  char **const argv = (char **)p_args;
  p_args += sizeof(char *) * (argc + 1);

  char **const envp = (char **)p_args;
  environ = envp;

  atexit(__libc_fini_array);

  __libc_init_array();
  exit(main(argc, argv, envp));

  assert(((void)"unreachable", 0));
}
