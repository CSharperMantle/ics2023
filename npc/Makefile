.PHONY: all sim

all: csrc/main.cpp vsrc/top.v
	verilator --cc --exe --build -j 0 -Wall csrc/main.cpp vsrc/top.v

sim:
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	obj_dir/Vtop

include ../Makefile
