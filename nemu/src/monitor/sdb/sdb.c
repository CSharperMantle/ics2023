/***************************************************************************************
 * Copyright (c) 2014-2022 Zihao Yu, Nanjing University
 *
 * NEMU is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 *
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
 * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
 * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 *
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/

#include "sdb.h"
#include "ckpt.h"
#include "cmd.h"
#include "utils.h"
#include <cpu/cpu.h>
#include <cpu/difftest.h>
#include <errno.h>
#include <isa.h>
#include <memory/vaddr.h>
#include <readline/history.h>
#include <readline/readline.h>
#include <stdio.h>

static bool is_batch_mode = false;

void init_regex(void);
#ifdef CONFIG_WATCHPOINT
void init_wp_pool(void);
#endif

/* We use the `readline' library to provide more flexibility to read from stdin. */
static char *rl_gets() {
  static char *line_read = NULL;

  if (line_read) {
    free(line_read);
    line_read = NULL;
  }

  line_read = readline("(nemu) ");

  if (line_read && *line_read) {
    add_history(line_read);
  }

  return line_read;
}

void sdb_set_batch_mode() {
  is_batch_mode = true;
}

void sdb_mainloop() {
  if (is_batch_mode) {
    cmd_c(NULL);
    return;
  }

  for (char *str; (str = rl_gets()) != NULL;) {
    char *str_end = str + strlen(str);

    /* extract the first token as the command */
    char *cmd = strtok(str, " ");
    if (cmd == NULL) {
      continue;
    }

    /* treat the remaining string as the arguments,
     * which may need further parsing
     */
    char *args = cmd + strlen(cmd) + 1;
    if (args >= str_end) {
      args = NULL;
    }

#ifdef CONFIG_DEVICE
    extern void sdl_clear_event_queue(void);
    sdl_clear_event_queue();
#endif

    size_t i;
    for (i = 0; i < NR_CMD; i++) {
      if (strcmp(cmd, CMD_TABLE[i].name) == 0) {
        if (CMD_TABLE[i].handler(args) < 0) {
          nemu_state.state = NEMU_END;
          return;
        }
        break;
      }
    }

    if (i == NR_CMD) {
      printf("Unknown command \"%s\"\n", cmd);
    }
  }
}

void init_sdb(char *elf_file) {
  /* Compile the regular expressions. */
  init_regex();

  IFDEF(CONFIG_WATCHPOINT, init_wp_pool());

#ifdef CONFIG_FTRACE
  cmd_file(elf_file);
#else
  (void)elf_file;
#endif
}
