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
#include <memory/vaddr.h>

/* We use the POSIX regex functions to process regular expressions.
 * Type 'man regex' for more information about POSIX regex functions.
 */
#include <regex.h>

enum {
  TK_NOTYPE = UINT8_MAX + 1,
  TK_EQ,
  TK_NEQ,
  TK_NUM,
  TK_NEG,
  TK_DEREF,
  TK_REG,
  TK_AND,
};

enum {
  PREC_AND = 0,
  PREC_EQ_NEQ,
  PREC_ADD_SUB,
  PREC_MUL_DIV,
  PREC_LEN_,
};

static struct rule {
  const char *regex;
  int token_type;
} rules[] = {
    {"\\s+", TK_NOTYPE},      // spaces
    {"\\+", '+'},             // plus
    {"-", '-'},               // minus
    {"\\*", '*'},             // multiply
    {"/", '/'},               // divide
    {"\\(", '('},             // left bracket
    {"\\)", ')'},             // right bracket
    {"==", TK_EQ},            // equal
    {"!=", TK_NEQ},           // not equal
    {"&&", TK_AND},           // logical and
    {"0x[0-9]+", TK_NUM},     // hex number
    {"[0-9]+", TK_NUM},       // dec number
    {"\\$[a-z0-9]+", TK_REG}, // register
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
      if (regexec(&re[i], e + position, 1, &pmatch, 0) == 0 && pmatch.rm_so == 0) {
        char *substr_start = e + position;
        int substr_len = pmatch.rm_eo;

        Log("match rules[%d] = \"%s\" at position %d with len %d: %.*s",
            i,
            rules[i].regex,
            position,
            substr_len,
            substr_len,
            substr_start);

        position += substr_len;

        Token token = (Token){
            .type = rules[i].token_type,
            .str = {0},
        };

        if (substr_len > ARRLEN(token.str) - 1) {
          printf("Token too long; %d bytes max\n", ARRLEN(token.str) - 1);
          return false;
        }

        strncpy(token.str, substr_start, substr_len);

        if (nr_token >= ARRLEN(tokens)) {
          printf("Too many tokens; %d tokens max\n", ARRLEN(tokens));
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
      printf("No match at position %d\n%s\n%*.s^\n", position, e, position, "");
      return false;
    }
  }

  for (i = 0; i < nr_token; i++) {
#define AFTER_EXPR_(t_) ((t_) == ')' || (t_) == TK_NUM || (t_) == TK_REG)
    switch (tokens[i].type) {
      case '*':
        if (i == 0 || !AFTER_EXPR_(tokens[i - 1].type)) {
          tokens[i].type = TK_DEREF;
        }
        break;
      case '-':
        if (i == 0 || !AFTER_EXPR_(tokens[i - 1].type)) {
          tokens[i].type = TK_NEG;
        }
        break;
      default:
        break;
    }
  }
#undef AFTER_EXPR_

  return true;
}

typedef enum {
  PAREN_NONE,
  PAREN_UNMATCHED,
  PAREN_WRAPPED,
} ParenStatus;

static ParenStatus check_parentheses(int p, int q) {
  int level = 0;
  bool has_paren = false;
  int last_rparen = q + 1;
  int i;
  for (i = p; i <= q; i++) {
    switch (tokens[i].type) {
      case '(':
        level++;
        has_paren = true;
        break;
      case ')':
        level--;
        has_paren = true;
        last_rparen = i;
        break;
      default:
        break;
    }
    if (level < 0) {
      return PAREN_UNMATCHED;
    }
    if (level == 0 && has_paren) {
      break;
    }
  }
  for (int j = last_rparen; j <= q; j++) {
    if (tokens[j].type == ')') {
      last_rparen = j;
    }
  }
  if (level != 0) {
    return PAREN_UNMATCHED;
  }
  if (has_paren && level == 0 && i == last_rparen && tokens[p].type == '(' &&
      tokens[q].type == ')') {
    return PAREN_WRAPPED;
  } else {
    return PAREN_NONE;
  }
}

static int find_main_op(int p, int q) {
  int level = 0;
  int indices[PREC_LEN_];
  for (int i = 0; i < PREC_LEN_; i++) {
    indices[i] = -1;
  }
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
      /* Change associativity for unary minus */
      switch (tokens[i].type) {
        case TK_AND:
          indices[PREC_AND] = i;
          break;
        case TK_EQ:
        case TK_NEQ:
          indices[PREC_EQ_NEQ] = i;
          break;
        case '*':
        case '/':
          indices[PREC_MUL_DIV] = i;
          break;
        case '+':
        case '-':
          indices[PREC_ADD_SUB] = i;
          break;
        default:
          break;
      }
    }
  }
  for (int i = 0; i < PREC_LEN_; i++) {
    if (indices[i] != -1) {
      return indices[i];
    }
  }
  return -1;
}

static word_t eval(int p, int q, bool *success) {
  if (p > q) {
    Log("(%d,%d): %s", p, q, "bad expression");
    printf("Bad expression at token #%d\n", q);
    *success = false;
    return 0;
  } else if (p == q) {
    switch (tokens[p].type) {
      case TK_NUM:
        Log("(%d,%d): single number token", p, q);
        return (word_t)strtoll(tokens[p].str, NULL, 0);
      case TK_REG: {
        Log("(%d,%d): single register token", p, q);
        bool reg_succ = false;
        /* Remove prefix "$" */
        word_t val = isa_reg_str2val(tokens[p].str + 1, &reg_succ);
        if (!reg_succ) {
          printf("Unknown register \"%s\" at token #%d\n", tokens[p].str, p);
          *success = false;
          return 0;
        }
        return val;
      }
      default:
        panic("unreachable (tokens[p].type == %d)", tokens[p].type);
    }
  }

  switch (check_parentheses(p, q)) {
    case PAREN_WRAPPED:
      Log("(%d,%d): parentheses reduction", p, q);
      return eval(p + 1, q - 1, success);
    case PAREN_UNMATCHED:
      Log("(%d,%d): ill-formed parentheses", p, q);
      printf("Ill-formed parentheses at tokens #%d-#%d\n", p, q);
      *success = false;
      return 0;
    case PAREN_NONE: {
      Log("(%d,%d): expression eval", p, q);
      int i_op = find_main_op(p, q);
      if (i_op == -1) {
        Log("(%d,%d): no main op found; check for unary op", p, q);
        switch (tokens[p].type) {
          case TK_NEG: {
            word_t val = eval(p + 1, q, success);
            return -val;
          }
          case TK_DEREF: {
            word_t addr = eval(p + 1, q, success);
            return *success ? vaddr_read((vaddr_t)addr, 4) : 0;
          }
          default: {
            Log("(%d,%d): not even a unary op at token #%d (%s); reporting",
                p,
                q,
                p,
                tokens[p].str);
            printf("Bad expression at token #%d\n", p);
            *success = false;
            return 0;
          }
        }
      }
      Log("(%d,%d): main op (%s) at token #%d", p, q, tokens[i_op].str, i_op);
      word_t val_1 = eval(p, i_op - 1, success);
      word_t val_2 = eval(i_op + 1, q, success);
      switch (tokens[i_op].type) {
        case '+':
          return val_1 + val_2;
        case '-':
          return val_1 - val_2;
        case '*':
          return val_1 * val_2;
        case '/':
          return val_1 / val_2;
        case TK_EQ:
          return val_1 == val_2;
        case TK_NEQ:
          return val_1 != val_2;
        case TK_AND:
          return (val_1 && val_2) ? 1 : 0;
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
