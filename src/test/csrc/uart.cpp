#include "common.h"
#include "stdlib.h"

#define QUEUE_SIZE 1024
static char queue[QUEUE_SIZE] = {};
static int f = 0, r = 0;

static void uart_enqueue(char ch) {
  int next = (r + 1) % QUEUE_SIZE;
  if (next != f) {
    // not full
    queue[r] = ch;
    r = next;
  }
}

static int uart_dequeue(void) {
  int k = 0;
  if (f != r) {
    k = queue[f];
    f = (f + 1) % QUEUE_SIZE;
  } else {
    // generate a random key every 1s for pal
    k = "uiojkl"[rand()% 6];
  }
  return k;
}

uint32_t uptime(void);
extern "C" void uart_getc(uint8_t *ch) {
  static uint32_t lasttime = 0;
  uint32_t now = uptime();

  *ch = 0;
  if (now - lasttime > 30000) {
    lasttime = now;
    *ch = uart_dequeue();
  }
}

void uart_putc(char c) {
  eprintf("%c", c);
}

static void preset_input() {
  char rtthread_cmd[128] = "memtrace\n";
  char init_cmd[128] = "2" // choose PAL
    "jjjjjjjkkkkkk" // walk to enemy
    ;
  char *buf = init_cmd;
  int i;
  for (i = 0; i < strlen(buf); i ++) {
    uart_enqueue(buf[i]);
  }
}

void init_uart(void) {
  preset_input();
}
