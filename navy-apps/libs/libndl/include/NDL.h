#ifndef __NDL_H__
#define __NDL_H__

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int NDL_Init(uint32_t flags);
void NDL_Quit(void);
uint32_t NDL_GetTicks(void);
void NDL_OpenCanvas(int *w, int *h);
int NDL_PollEvent(char *buf, int len);
void NDL_DrawRect(uint32_t *pixels, int x, int y, int w, int h);
void NDL_OpenAudio(int freq, int channels, int samples);
void NDL_CloseAudio(void);
int NDL_PlayAudio(void *buf, int len);
int NDL_QueryAudio(void);

#ifdef __cplusplus
}
#endif

#endif
