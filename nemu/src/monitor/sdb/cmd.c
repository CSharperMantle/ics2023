#include "cmd.h"
#include "ckpt.h"
#include "sdb.h"
#include "utils.h"
#include <cpu/cpu.h>
#include <cpu/difftest.h>
#include <errno.h>
#include <isa.h>
#include <memory/vaddr.h>
#include <readline/history.h>
#include <readline/readline.h>
#include <stdio.h>

const CmdEntry_t CMD_TABLE[] = {
    {"help",   "Display information about all supported commands",    cmd_help  },
    {"c",      "Continue the execution of the program",               cmd_c     },
    {"q",      "Exit NEMU",                                           cmd_q     },
    {"si",     "Step N instructions",                                 cmd_si    },
    {"info",   "Display execution status and information",            cmd_info  },
    {"x",      "Print N dwords in memory starting from address EXPR", cmd_x     },
    {"p",      "Evaluate EXPR",                                       cmd_p     },
    {"save",   "Save NEMU snapshot to FILENAME",                      cmd_save  },
    {"load",   "Load NEMU snapshot from FILENAME",                    cmd_load  },
#ifdef CONFIG_WATCHPOINT
    {"w",      "Set up watchpoint for EXPR",                          cmd_w     },
    {"d",      "Delete watchpoint N",                                 cmd_d     },
#endif
#ifdef CONFIG_FTRACE
    {"file",   "Load symbols from FILENAME",                          cmd_file  },
#endif
#ifdef CONFIG_DIFFTEST
    {"attach", "Enable difftest",                                     cmd_attach},
    {"detach", "Disable difftest",                                    cmd_detach},
#endif
};

const size_t NR_CMD = ARRLEN(CMD_TABLE);

int cmd_c(char *args) {
  cpu_exec(-1);
  return 0;
}

int cmd_q(char *args) {
  return -1;
}

int cmd_help(char *args) {
  char *arg = strtok(args, " ");
  if (arg == NULL) {
    for (size_t i = 0; i < NR_CMD; i++) {
      printf("%s - %s\n", CMD_TABLE[i].name, CMD_TABLE[i].description);
    }
  } else {
    for (size_t i = 0; i < NR_CMD; i++) {
      if (strcmp(arg, CMD_TABLE[i].name) == 0) {
        printf("%s - %s\n", CMD_TABLE[i].name, CMD_TABLE[i].description);
        return 0;
      }
    }
    printf("Unknown command \"%s\"\n", arg);
  }
  return 0;
}

int cmd_si(char *args) {
  char *arg = strtok(NULL, " ");
  if (arg == NULL) {
    cpu_exec(1);
  } else {
    int n = atoi(arg);
    cpu_exec(n);
  }
  return 0;
}

int cmd_info(char *args) {
  char *arg = strtok(NULL, " ");
  if (arg == NULL) {
    puts("No argument given");
    return 0;
  }
  if (strcmp(arg, "r") == 0) {
    isa_reg_display();
  }
#ifdef CONFIG_WATCHPOINT
  else if (strcmp(arg, "w") == 0) {
    char *arg2 = strtok(NULL, " ");
    if (arg2 == NULL) {
      watchpoint_print_all();
    } else {
      watchpoint_print_at(atoi(arg2));
    }
  }
#endif
  else {
    printf("Unknown argument \"%s\"\n", arg);
  }
  return 0;
}

int cmd_x(char *args) {
  char *arg1 = strtok(NULL, " ");
  char *arg2 = strtok(NULL, " ");
  if (arg1 == NULL || arg2 == NULL) {
    puts("Two arguments required");
    return 0;
  }
  const unsigned long n = strtoul(arg1, NULL, 0);
#ifdef CONFIG_RV64
  const vaddr_t addr = strtoul(arg2, NULL, 16);
#else
  const unsigned long addr_ul = strtoul(arg2, NULL, 16);
  if ((addr_ul & ~0xfffffffful) != 0 || ((addr_ul + n * 4) & ~0xfffffffful) != 0) {
    printf("Address range is not within [0, 2^%d - 1]\n", XLEN);
    return 0;
  }
  const vaddr_t addr = (vaddr_t)addr_ul;
#endif
  for (size_t i = 0; i < n; i++) {
    const word_t offset = (word_t)(i * 4);
    printf(FMT_WORD ": " FMT_WORD "\n", addr + offset, vaddr_read(addr + offset, 4));
  }
  return 0;
}

int cmd_p(char *args) {
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

int cmd_save(char *args) {
  if (args == NULL) {
    puts("One argument required");
    return 0;
  }
  if (ckpt_save_to(args)) {
    printf("Cannot save checkpoint \"%s\"\n", args);
  }
  return 0;
}

int cmd_load(char *args) {
  if (args == NULL) {
    puts("One argument required");
    return 0;
  }
  if (ckpt_load_from(args)) {
    printf("Cannot load checkpoint \"%s\"\n", args);
  }
  return 0;
}

#ifdef CONFIG_WATCHPOINT
int cmd_w(char *args) {
  if (args == NULL) {
    puts("One argument required");
    return 0;
  }
  watchpoint_add(args);
  return 0;
}

int cmd_d(char *args) {
  if (args == NULL) {
    puts("One argument required");
    return 0;
  }
  int no = atoi(args);
  watchpoint_delete(no);
  return 0;
}
#endif

#ifdef CONFIG_FTRACE
int cmd_file(char *args) {
  if (args == NULL) {
    elf.valid = false;
    puts("No symbol file now.");
    return 0;
  }
  FILE *felf = fopen(args, "rb");
  if (felf == NULL) {
    printf("Cannot open ELF file \"%s\": errno %d: %s\n", args, errno, strerror(errno));
    return 0;
  }
  printf("Reading symbols from \"%s\"\n", args);
  if (elf_read(felf)) {
    printf("Cannot parse ELF file \"%s\"\n", args);
  };
  fclose(felf);
  return 0;
}
#endif

#ifdef CONFIG_DIFFTEST
int cmd_attach(char *args) {
  difftest_attach();
  return 0;
}

int cmd_detach(char *args) {
  difftest_detach();
  return 0;
}
#endif