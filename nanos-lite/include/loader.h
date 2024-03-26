#ifndef LOADER_H_INCLUDED_
#define LOADER_H_INCLUDED_

#include <proc.h>

void naive_uload(PCB *pcb, const char *filename);
void context_kload(PCB *pcb, void (*entry)(void *), void *arg);

#endif
