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

#ifndef NPC_DEBUG_HPP_
#define NPC_DEBUG_HPP_

#include "common.hpp"
#include "utils.hpp"
#include <cstdio>

#define Log(format, ...)                                                                           \
  _Log(ANSI_FMT("[%s:%d %s]", ANSI_FG_BLUE) " " format "\n",                                       \
       __FILE__,                                                                                   \
       __LINE__,                                                                                   \
       __func__,                                                                                   \
       ##__VA_ARGS__)

#define Warn(format, ...)                                                                          \
  _Log(ANSI_FMT("[%s:%d %s]", ANSI_FG_BRIGHT_YELLOW) " " format "\n",                              \
       __FILE__,                                                                                   \
       __LINE__,                                                                                   \
       __func__,                                                                                   \
       ##__VA_ARGS__)

#define Error(format, ...)                                                                         \
  _Log(ANSI_FMT("[%s:%d %s]", ANSI_FG_RED) " " format "\n",                                        \
       __FILE__,                                                                                   \
       __LINE__,                                                                                   \
       __func__,                                                                                   \
       ##__VA_ARGS__)

#define Assert(cond, format, ...)                                                                  \
  do {                                                                                             \
    if (!(cond)) {                                                                                 \
      MUXDEF(                                                                                      \
          CONFIG_TARGET_AM,                                                                        \
          printf(ANSI_FMT(format, ANSI_FG_RED) "\n", ##__VA_ARGS__),                               \
          (fflush(stdout), fprintf(stderr, ANSI_FMT(format, ANSI_FG_RED) "\n", ##__VA_ARGS__)));   \
      assert(cond);                                                                                \
    }                                                                                              \
  } while (0)

#define panic(format, ...)                                                                         \
  do {                                                                                             \
    Assert(0, format, ##__VA_ARGS__);                                                              \
    unreachable();                                                                                 \
  } while (0)

#define TODO() panic("please implement me")

#endif
