#include <assert.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>
#include <unistd.h>

static int evtdev = -1;
static int fbdev = -1;
static int screen_w = 0, screen_h = 0;
static int canvas_off_x = 0, canvas_off_y = 0;

uint32_t NDL_GetTicks(void) {
  struct timeval tv;
  gettimeofday(&tv, NULL);
  return (uint32_t)(tv.tv_sec * 1000 + tv.tv_usec / 1000);
}

int NDL_PollEvent(char *buf, int len) {
  return read(evtdev, buf, len);
}

void NDL_OpenCanvas(int *w, int *h) {
  assert(*w >= 0 && *w <= screen_w);
  assert(*h >= 0 && *h <= screen_h);

  const int orig_w = *w;
  const int orig_h = *h;
  *w = (orig_w == 0 ? screen_w : orig_w);
  *h = (orig_h == 0 ? screen_h : orig_h);

  canvas_off_x = (screen_w - *w) / 2;
  canvas_off_y = (screen_h - *h) / 2;

  if (getenv("NWM_APP")) {
    int fbctl = 4;
    fbdev = 5;
    screen_w = *w;
    screen_h = *h;
    char buf[64];
    int len = snprintf(buf, sizeof(buf), "%d %d", screen_w, screen_h);
    // let NWM resize the window and create the frame buffer
    write(fbctl, buf, len);
    while (1) {
      // 3 = evtdev
      int nread = read(3, buf, sizeof(buf) - 1);
      if (nread <= 0)
        continue;
      buf[nread] = '\0';
      if (strcmp(buf, "mmap ok") == 0)
        break;
    }
    close(fbctl);
  }
}

void NDL_DrawRect(uint32_t *pixels, int x, int y, int w, int h) {
  const int ffb = open("/dev/fb", O_WRONLY);
  for (int i_y = 0; i_y < h; i_y++) {
    lseek(ffb,
          ((canvas_off_y + y + i_y) * screen_w + (canvas_off_x + x)) * sizeof(uint32_t),
          SEEK_SET);
    const uint32_t *const pixels_row = &pixels[i_y * w];
    write(ffb, pixels_row, w * sizeof(uint32_t));
  }
  close(ffb);
}

void NDL_OpenAudio(int freq, int channels, int samples) {
  const int fsbctl = open("/dev/sbctl", O_WRONLY);
  const union {
    struct fields_ {
      int freq;
      int channels;
      int samples;
    } __attribute__((packed)) fields;
    uint8_t as_bytes[sizeof(struct fields_)];
  } data = {
      .fields = {
                 .freq = freq,
                 .channels = channels,
                 .samples = samples,
                 }
  };
  write(fsbctl, data.as_bytes, sizeof(data.as_bytes));
  close(fsbctl);
}

void NDL_CloseAudio(void) {
  // no-op
  ;
}

int NDL_PlayAudio(void *buf, int len) {
  const int fsb = open("/dev/sb", O_WRONLY);
  const ssize_t ret = write(fsb, buf, len);
  close(fsb);
  return (int)ret;
}

int NDL_QueryAudio(void) {
  const int fsbctl = open("/dev/sbctl", O_RDONLY);
  int count;
  const ssize_t ret = read(fsbctl, &count, sizeof(count));
  if (ret < 0) {
    return -1;
  }
  close(fsbctl);
  return count;
}

int NDL_Init(uint32_t flags) {
  if (getenv("NWM_APP")) {
    evtdev = 3;
  } else {
    evtdev = open("/dev/events", O_RDONLY);
  }

  int fdispinfo = open("/proc/dispinfo", O_RDONLY);

  char buf[64];
  ssize_t ret = read(fdispinfo, buf, sizeof(buf));
  if (ret < 0) {
    return -1;
  }

  int width, height;
  int n_matches = sscanf(buf, "WIDTH: %d HEIGHT:%d", &width, &height);
  if (n_matches != 2) {
    close(fdispinfo);
    return -1;
  }
  screen_w = width;
  screen_h = height;

  close(fdispinfo);

  return 0;
}

void NDL_Quit(void) {
  if (!getenv("NWM_APP")) {
    close(evtdev);
  }
}
