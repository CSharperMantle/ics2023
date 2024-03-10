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
#include <cpu/cpu.h>
#include <isa.h>
#include <memory/vaddr.h>
#include <readline/history.h>
#include <readline/readline.h>

static int is_batch_mode = false;

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

static int cmd_c(char *args);
static int cmd_q(char *args);
static int cmd_help(char *args);
static int cmd_si(char *args);
static int cmd_info(char *args);
static int cmd_x(char *args);
static int cmd_p(char *args);

#ifdef CONFIG_WATCHPOINT
static int cmd_w(char *args);
static int cmd_d(char *args);
#endif

static struct {
  const char *name;
  const char *description;
  int (*handler)(char *);
} cmd_table[] = {
    {"help", "Display information about all supported commands",    cmd_help},
    {"c",    "Continue the execution of the program",               cmd_c   },
    {"q",    "Exit NEMU",                                           cmd_q   },
    {"si",   "Step N instructions",                                 cmd_si  },
    {"info", "Display execution status and information",            cmd_info},
    {"x",    "Print N dwords in memory starting from address EXPR", cmd_x   },
    {"p",    "Evaluate EXPR",                                       cmd_p   },
#ifdef CONFIG_WATCHPOINT
    {"w",    "Set up watchpoint for EXPR",                          cmd_w   },
    {"d",    "Delete watchpoint N",                                 cmd_d   },
#endif
};

#define NR_CMD ARRLEN(cmd_table)

static int cmd_c(char *args) {
  cpu_exec(-1);
  return 0;
}

static int cmd_q(char *args) {
  return -1;
}

static int cmd_help(char *args) {
  char *arg = strtok(args, " ");
  if (arg == NULL) {
    for (int i = 0; i < NR_CMD; i++) {
      printf("%s - %s\n", cmd_table[i].name, cmd_table[i].description);
    }
  } else {
    for (int i = 0; i < NR_CMD; i++) {
      if (strcmp(arg, cmd_table[i].name) == 0) {
        printf("%s - %s\n", cmd_table[i].name, cmd_table[i].description);
        return 0;
      }
    }
    printf("Unknown command '%s'\n", arg);
  }

  return 0;
}

static int cmd_si(char *args) {
  char *arg = strtok(NULL, " ");

  if (arg == NULL) {
    cpu_exec(1);
  } else {
    int n = atoi(arg);
    cpu_exec(n);
  }

  return 0;
}

static int cmd_info(char *args) {
  char *arg = strtok(NULL, " ");

  if (arg == NULL) {
    puts("No argument given");
    return 0;
  }
  if (strcmp(arg, "r") == 0) {
    isa_reg_display();
  } else if (strcmp(arg, "w") == 0) {
    char *arg2 = strtok(NULL, " ");
    if (arg2 == NULL) {
      watchpoint_print_all();
    } else {
      watchpoint_print_at(atoi(arg2));
    }
  } else {
    printf("Unknown argument '%s'\n", arg);
  }

  return 0;
}

static int cmd_x(char *args) {
  char *arg1 = strtok(NULL, " ");
  char *arg2 = strtok(NULL, " ");

  if (arg1 == NULL || arg2 == NULL) {
    puts("Two arguments required");
    return 0;
  }
  int n = atoi(arg1);
  vaddr_t addr = strtol(arg2, NULL, 16);
  for (int i = 0; i < n; i++) {
    int offset = i * 4;
    printf(FMT_WORD ": " FMT_WORD "\n", addr + offset, vaddr_read(addr + offset, 4));
  }
  return 0;
}

static int cmd_p(char *args) {
  if (args == NULL) {
    puts("One argument required");
    return 0;
  }
  bool success = false;
  word_t value = expr(args, &success);
  if (success) {
    printf(FMT_WORD "\n", value);
  } else {
    puts("Cannot parse expression");
  }
  return 0;
}

#ifdef CONFIG_WATCHPOINT

static int cmd_w(char *args) {
  if (args == NULL) {
    puts("One argument required");
    return 0;
  }
  watchpoint_add(args);
  return 0;
}

static int cmd_d(char *args) {
  if (args == NULL) {
    puts("One argument required");
    return 0;
  }
  int no = atoi(args);
  watchpoint_delete(no);
  return 0;
}

#endif

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
    extern void sdl_clear_event_queue();
    sdl_clear_event_queue();
#endif

    int i;
    for (i = 0; i < NR_CMD; i++) {
      if (strcmp(cmd, cmd_table[i].name) == 0) {
        if (cmd_table[i].handler(args) < 0) {
          return;
        }
        break;
      }
    }

    if (i == NR_CMD) {
      printf("Unknown command '%s'\n", cmd);
    }
  }
}

void init_sdb() {
  /* Compile the regular expressions. */
  init_regex();

  IFDEF(CONFIG_WATCHPOINT, init_wp_pool());
}
