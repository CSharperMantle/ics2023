#ifndef __COMMON_H__
#define __COMMON_H__

/* Uncomment these macros to enable corresponding functionality. */
#define HAS_CTE
//#define CONFIG_STRACE
//#define CONFIG_SCHEDTRACE
#define HAS_VME
//#define MULTIPROGRAM
//#define TIME_SHARING

#include <am.h>
#include <klib-macros.h>
#include <klib.h>

#include <debug.h>

#define ARRLEN(arr_) (sizeof(arr_) / sizeof(arr_[0]))
#define MIN(a_, b_)  ((a_) <= (b_) ? (a_) : (b_))

#endif
