#ifndef CMD_H_INCLUDED_
#define CMD_H_INCLUDED_

#include "sdb.h"

typedef struct CmdEntry_ {
  const char *name;
  const char *description;
  int (*const handler)(char *);
} CmdEntry_t;

extern const CmdEntry_t CMD_TABLE[];
extern const size_t NR_CMD;

int cmd_c(char *args);
int cmd_q(char *args);
int cmd_help(char *args);
int cmd_si(char *args);
int cmd_info(char *args);
int cmd_x(char *args);
int cmd_p(char *args);
int cmd_save(char *args);
int cmd_load(char *args);
#ifdef CONFIG_WATCHPOINT
int cmd_w(char *args);
int cmd_d(char *args);
#endif
#ifdef CONFIG_FTRACE
int cmd_file(char *args);
#endif
#ifdef CONFIG_DIFFTEST
int cmd_attach(char *args);
int cmd_detach(char *args);
#endif

#endif