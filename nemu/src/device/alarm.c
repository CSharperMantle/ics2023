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

#include <common.h>
#include <device/alarm.h>
#include <signal.h>
#include <sys/time.h>

#define MAX_HANDLER 8

static alarm_handler_t handlers[MAX_HANDLER] = {};
static size_t n_handlers = 0;

void add_alarm_handle(alarm_handler_t h) {
  assert(n_handlers < MAX_HANDLER);
  handlers[n_handlers++] = h;
}

static void alarm_sig_handler(int signum) {
  for (size_t i = 0; i < n_handlers; i++) {
    handlers[i]();
  }
}

void init_alarm(void) {
  int ret;

  const struct sigaction sig = {
      .sa_handler = alarm_sig_handler,
  };
  ret = sigaction(SIGVTALRM, &sig, NULL);
  Assert(ret == 0, "Can not set signal handler");

  const struct timeval interval = {
      .tv_sec = 0,
      .tv_usec = 1000000 / TIMER_HZ,
  };
  const struct itimerval it = {
      .it_interval = interval,
      .it_value = interval,
  };
  ret = setitimer(ITIMER_VIRTUAL, &it, NULL);
  Assert(ret == 0, "Can not set timer");
}
