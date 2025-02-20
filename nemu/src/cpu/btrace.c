#include <cpu/btrace.h>
#include <isa.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/wait.h>
#include <unistd.h>

FILE *file_btrace = NULL;

#ifdef CONFIG_BTRACE

static pid_t pid_zstd = -1;

void init_btrace(const char *path) {
  close_btrace();
  if (path == NULL) {
    return;
  }

  int pipefd[2];
  AssertErrno(pipe(pipefd) == 0, "pipe");
  pid_zstd = fork();
  AssertErrno(pid_zstd >= 0, "fork");

  if (pid_zstd == 0) {
    close(pipefd[1]);
    dup2(pipefd[0], STDIN_FILENO);
    execlp(CONFIG_BTRACE_ZSTD_CMD,
           CONFIG_BTRACE_ZSTD_CMD,
           "-f",
           "-T0",
           "-10",
           "-q",
           "-q",
           "-",
           "-o",
           path,
           NULL);
    AssertErrno(false, "execlp");
  }

  close(pipefd[0]);
  file_btrace = fdopen(pipefd[1], "wb");
  AssertErrno(file_btrace != NULL, "fdopen");
  setbuf(file_btrace, NULL);
}

void write_btrace(word_t pc, BTraceEntryType_t type, bool taken, word_t target) {
  if (file_btrace != NULL) {
    const BTraceEntry_t entry = {
        .pc.as_word_t = pc,
        .target.as_word_t = target,
        .type = type,
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
  if (file_btrace == NULL) {
    return;
  }
  fclose(file_btrace);
  file_btrace = NULL;
  int status;
  AssertErrno(waitpid(pid_zstd, &status, 0) >= 0, "waitpid");
  Assert(WIFEXITED(status), "compression subprocess failed to terminate normally");
  Assert(WEXITSTATUS(status) == 0,
         "compression subprocess exited with non-zero code: %d",
         WEXITSTATUS(status));
}

#else

void init_btrace(const char *path) {}

void write_btrace(word_t pc, bool taken) {}

void flush_btrace(void) {}

void close_btrace(void) {}

#endif
