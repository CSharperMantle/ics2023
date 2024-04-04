#ifndef LOADER_H_INCLUDED_
#define LOADER_H_INCLUDED_

#include <proc.h>

void naive_uload(PCB *pcb, const char *filename);
void context_kload(PCB *pcb, void (*entry)(void *), void *arg);
void context_uload(PCB *pcb, const char *filename, char *const argv[], char *const envp[]);

#endif
