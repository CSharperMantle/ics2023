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

#ifdef CONFIG_WATCHPOINT

static WP wp_pool[NR_WP] = {};
static WP *head = NULL, *free_ = NULL;

void init_wp_pool(void) {
  int i;
  for (i = 0; i < NR_WP; i++) {
    wp_pool[i] = (WP){
        .NO = i,
        .next = (i == NR_WP - 1 ? NULL : &wp_pool[i + 1]),
    };
  }

  head = NULL;
  free_ = wp_pool;
}

static WP *new_wp(void) {
  if (free_ == NULL) {
    return NULL;
  }
  WP *new_node = free_;
  free_ = free_->next;
  new_node->next = head;
  head = new_node;
  return new_node;
}

static void free_wp(WP *wp) {
  Assert(wp != NULL, "wp is not null");

  if (head == wp) {
    head = head->next;
    return;
  }

  WP *p = head;
  while (p != NULL) {
    if (p->next == wp) {
      break;
    }
  }
  Assert(p != NULL, "double free detected");
  p->next = wp->next;
  if (free_ == NULL) {
    free_ = wp;
    wp->next = NULL;
  } else {
    wp->next = free_;
    free_ = wp;
  }
}

static WP *watchpoint_find(int no) {
  WP *p = head;
  while (p != NULL) {
    if (p->NO == no) {
      return p;
    }
    p = p->next;
  }
  return NULL;
}

static void watchpoint_print_banner(void) {
  puts("Num\tExpr");
}

WP *watchpoint_head(void) {
  return head;
}

void watchpoint_print_all(void) {
  watchpoint_print_banner();
  WP *p = head;
  while (p != NULL) {
    printf("%d\t%s\n", p->NO, p->expr);
    p = p->next;
  }
}

void watchpoint_print_at(int no) {
  WP *p = watchpoint_find(no);
  if (p == NULL) {
    printf("Watchpoint %d not found\n", no);
    return;
  }
  watchpoint_print_banner();
  printf("%d\t%s\n", p->NO, p->expr);
}

void watchpoint_add(const char *e) {
  WP *obj = new_wp();
  if (obj == NULL) {
    printf("Too many watchpoints; %d max\n", NR_WP);
    return;
  }
  if (strlen(e) > WP_MAX_EXPR_LEN) {
    printf("Watchpoint expression too long; %d max", WP_MAX_EXPR_LEN);
    return;
  }
  memset(obj->expr, 0, ARRLEN(obj->expr));
  strncpy(obj->expr, e, WP_MAX_EXPR_LEN);
  obj->prev_state = 0;
  printf("Watchpoint %d: %s\n", obj->NO, obj->expr);
}

void watchpoint_delete(int no) {
  WP *p = watchpoint_find(no);
  if (p == NULL) {
    printf("Watchpoint %d not found\n", no);
    return;
  }
  free_wp(p);
}

#endif