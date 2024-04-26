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

#include <assert.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define MAX_DEPTH 10
#define SIZE_BUF  131072

// this should be enough
static char buf[SIZE_BUF] = {0};
static size_t len_buf = 0;
static char code_buf[SIZE_BUF + 128] = {0}; // a little larger than `buf`
static const char *const CODE_FORMAT = "#include <stdio.h>\n"
                                       "int main(void) {\n"
                                       "  unsigned result = %s;\n"
                                       "  printf(\"%%u\", result);\n"
                                       "  return 0;\n"
                                       "}";

static int gen(char ch) {
  if (len_buf + 1 >= sizeof(buf)) {
    return 1;
  }
  buf[len_buf++] = ch;
  return 0;
}

static int gen_num(void) {
  const unsigned x = (unsigned)((double)UINT_MAX * ((double)rand() / (double)RAND_MAX));
  const size_t len_str = (size_t)snprintf(NULL, 0, "%uu", x);
  if (len_buf + len_str >= sizeof(buf)) {
    return 1;
  }
  len_buf += sprintf(buf + len_buf, "%uu", x);
  return 0;
}

static int gen_rand_op(void) {
  switch (rand() % 4) {
    case 0: return gen('+');
    case 1: return gen('-');
    case 2: return gen('*');
    default: return gen('/');
  }
}

static int gen_rand_expr(void) {
  // buf[len_buf] = '\0';
  switch (rand() % 3) {
    case 0: return gen_num();
    case 1: return gen('(') || gen_rand_expr() || gen(')');
    default: return gen_rand_expr() || gen_rand_op() || gen_rand_expr();
  }
}

int main(int argc, char *argv[]) {
  srand(time(0));
  size_t loop = 1;
  if (argc > 1) {
    sscanf(argv[1], "%zu", &loop);
  }
  for (size_t i = 0; i < loop; i++) {
    len_buf = 0;
    gen_rand_expr();
    buf[len_buf] = '\0';

    snprintf(code_buf, sizeof(code_buf), CODE_FORMAT, buf);

    FILE *fp = fopen("/tmp/.code.c", "w");
    assert(fp != NULL);
    fputs(code_buf, fp);
    fclose(fp);

    int ret = system("gcc -Wall -Werror -std=c17 -O1 /tmp/.code.c -o /tmp/.expr");
    if (ret != 0) {
      i--;
      fputs("W: Bad expression; retrying\n", stderr);
      continue;
    }

    fp = popen("/tmp/.expr", "r");
    assert(fp != NULL);

    unsigned result;
    ret = fscanf(fp, "%u", &result);
    pclose(fp);

    printf("%u %s\n", result, buf);
  }
  return 0;
}
