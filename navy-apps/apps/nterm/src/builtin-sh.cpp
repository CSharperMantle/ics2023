#include "sys/unistd.h"
#include <SDL.h>
#include <errno.h>
#include <nterm.h>
#include <stdarg.h>
#include <string.h>
#include <unistd.h>

char handle_key(SDL_Event *ev);

static void sh_printf(const char *format, ...) {
  static char buf[256] = {};
  va_list ap;
  va_start(ap, format);
  int len = vsnprintf(buf, 256, format, ap);
  va_end(ap);
  term->write(buf, len);
}

static void sh_banner() {
  sh_printf("Built-in Shell in NTerm (NJU Terminal)\n\n");
}

static void sh_prompt() {
  sh_printf("sh> ");
}

static void sh_handle_cmd(const char *cmd) {
  static char bin_name[256];

  const size_t len = strlen(cmd);
  char *const buf = (char *)malloc(sizeof(char) * (len + 1));
  strcpy(buf, cmd);

  size_t n_args = 0;
  char *tok = strtok(buf, " ");
  while (tok != NULL) {
    if (n_args == 0) {
      // bin name
      const size_t nonwhite_seg = strcspn(tok, " \f\n\r\t\v");
      if (nonwhite_seg > sizeof(bin_name) - 1) {
        sh_printf("Binary name too long: %zu; %zu max\n", nonwhite_seg, sizeof(bin_name) - 1);
      } else if (nonwhite_seg == 0) {
        // blank line
        free(buf);
        return;
      }
      memcpy(bin_name, tok, nonwhite_seg);
      bin_name[nonwhite_seg] = '\0';
    } else {
      // args
      // TODO: args
    }

    n_args++;
    tok = strtok(NULL, " ");
  }

  free(buf);

  if (!strcmp(bin_name, "exit")) {
    exit(0);
  }

  setenv("PATH", "/bin", false);
  const int ret = execvp(bin_name, NULL);
  if (ret == -1) {
    sh_printf("%s: cannot execvp\n", bin_name);
  }
}

void builtin_sh_run() {
  sh_banner();
  sh_prompt();

  while (1) {
    SDL_Event ev;
    if (SDL_PollEvent(&ev)) {
      if (ev.type == SDL_KEYUP || ev.type == SDL_KEYDOWN) {
        const char *res = term->keypress(handle_key(&ev));
        if (res) {
          sh_handle_cmd(res);
          sh_prompt();
        }
      }
    }
    refresh_terminal();
  }
}
