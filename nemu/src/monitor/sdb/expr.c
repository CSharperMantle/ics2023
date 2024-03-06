/***************************************************************************************
 * Copyright (c) 2014-2022 Zihao Yu, Nanjing University
 *
 * NEMU is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan
 *PSL v2. You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 *
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY
 *KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 *NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 *
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/

#include <isa.h>

/* We use the POSIX regex functions to process regular expressions.
 * Type 'man regex' for more information about POSIX regex functions.
 */
#include <regex.h>

enum {
  TK_NOTYPE = 256,
  TK_EQ,
  TK_NUM,
  TK_NEG,

  /* TODO: Add more token types */

};

static struct rule {
  const char *regex;
  int token_type;
} rules[] = {

    /* TODO: Add more rules.
     * Pay attention to the precedence level of different rules.
     */

    {"\\s+", TK_NOTYPE}, // spaces
    {"\\+", '+'},        // plus
    {"-", '-'},          // minus
    {"\\*", '*'},        // multiply
    {"/", '/'},          // divide
    {"\\(", '('},        // left bracket
    {"\\)", ')'},        // right bracket
    {"==", TK_EQ},       // equal
    {"[0-9]+", TK_NUM},  // number
};

#define NR_REGEX ARRLEN(rules)

static regex_t re[NR_REGEX] = {};

/* Rules are used for many times.
 * Therefore we compile them only once before any usage.
 */
void init_regex() {
  int i;
  char error_msg[128];
  int ret;

  for (i = 0; i < NR_REGEX; i++) {
    ret = regcomp(&re[i], rules[i].regex, REG_EXTENDED);
    if (ret != 0) {
      regerror(ret, &re[i], error_msg, 128);
      panic("regex compilation failed: %s\n%s", error_msg, rules[i].regex);
    }
  }
}

typedef struct token {
  int type;
  char str[32];
} Token;

static Token tokens[32] = {0};
static int nr_token = 0;

static bool make_token(char *e) {
  int position = 0;
  int i;
  regmatch_t pmatch;

  nr_token = 0;

  while (e[position] != '\0') {
    /* Try all rules one by one. */
    for (i = 0; i < NR_REGEX; i++) {
      if (regexec(&re[i], e + position, 1, &pmatch, 0) == 0 &&
          pmatch.rm_so == 0) {
        char *substr_start = e + position;
        int substr_len = pmatch.rm_eo;

        Log("match rules[%d] = \"%s\" at position %d with len %d: %.*s", i,
            rules[i].regex, position, substr_len, substr_len, substr_start);

        position += substr_len;

        Token token = (Token){
            .type = rules[i].token_type,
            .str = {0},
        };

        if (substr_len > ARRLEN(token.str) - 1) {
          printf("token too long; %d bytes max\n", ARRLEN(token.str) - 1);
          return false;
        }

        strncpy(token.str, substr_start, ARRLEN(token.str));

        if (nr_token >= ARRLEN(tokens)) {
          printf("too many tokens; %d tokens max\n", ARRLEN(tokens));
          return false;
        }
        tokens[nr_token++] = token;

        switch (rules[i].token_type) {
          case TK_NOTYPE:
            nr_token--;
            break;
          default:
            break;
        }

        break;
      }
    }

    if (i == NR_REGEX) {
      printf("no match at position %d\n%s\n%*.s^\n", position, e, position, "");
      return false;
    }
  }

  return true;
}

typedef enum {
  PAREN_UNWRAPPED,
  PAREN_UNMATCHED,
  PAREN_WRAPPED,
} ParenStatus;

static ParenStatus check_parentheses(int p, int q) {
  int level = 0;
  for (int i = p; i <= q; i++) {
    switch (tokens[i].type) {
      case '(':
        level++;
        break;
      case ')':
        level--;
        break;
      default:
        break;
    }
    if (level < 0) {
      return PAREN_UNMATCHED;
    }
  }
  if (level != 0) {
    return PAREN_UNMATCHED;
  }
  if (tokens[p].type == '(' && tokens[q].type == ')' && level == 0) {
    return PAREN_WRAPPED;
  } else {
    return PAREN_UNWRAPPED;
  }
}

static int find_main_op(int p, int q) {
  int level = 0;
  int idx_main_mul_div = -1;
  int idx_main_add_sub = -1;
  for (int i = p; i <= q; i++) {
    switch (tokens[i].type) {
      case '(':
        level++;
        continue;
      case ')':
        level--;
        continue;
      default:
        break;
    }
    if (level == 0) {
      switch (tokens[i].type) {
        case '*':
        case '/':
          idx_main_mul_div = i;
          break;
        case '+':
        case '-':
          idx_main_add_sub = i;
          break;
        default:
          break;
      }
    }
  }
  Assert(idx_main_mul_div != -1 || idx_main_add_sub != -1, "no operator found");
  return idx_main_add_sub != -1 ? idx_main_add_sub : idx_main_mul_div;
}

static word_t eval(int p, int q, bool *success) {
  if (p > q) {
    Log("(%d,%d): %s", p, q, "bad expression");
    printf("bad expression at token #%d\n", q);
    *success = false;
    return 0;
  } else if (p == q) {
    Log("(%d,%d): %s", p, q, "single token");
    return atoi(tokens[p].str);
  }

  switch (check_parentheses(p, q)) {
    case PAREN_WRAPPED:
      Log("(%d,%d): %s", p, q, "parentheses reduction");
      return eval(p + 1, q - 1, success);
    case PAREN_UNMATCHED:
      Log("(%d,%d): %s", p, q, "ill-formed parentheses");
      printf("ill-formed parentheses at tokens #%d-#%d\n", p, q);
      *success = false;
      return 0;
    case PAREN_UNWRAPPED: {
      Log("(%d,%d): %s", p, q, "expression eval");
      int op = find_main_op(p, q);
      word_t val_1 = eval(p, op - 1, success);
      word_t val_2 = eval(op + 1, q, success);
      switch (tokens[op].type) {
        case '+':
          return val_1 + val_2;
        case '-':
          return val_1 - val_2;
        case '*':
          return val_1 * val_2;
        case '/':
          return val_1 / val_2;
        default:
          panic("unreachable");
      }
    }
    default:
      panic("unreachable");
  }
}

word_t expr(char *e, bool *success) {
  *success = true;

  if (!make_token(e)) {
    *success = false;
    return 0;
  }

  return eval(0, nr_token - 1, success);
}
