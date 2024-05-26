BUILD_DIR = ./build

OBJ_DIR = $(BUILD_DIR)/obj_dir
BIN = $(BUILD_DIR)/$(TOP_NAME)

PRJ_NAME = playground

TOP_NAME = Top
NXDC_FILES = constr/top.nxdc
INC_PATH ?= ./include

VERILATOR = verilator
VERILATOR_CFLAGS += -MMD --build -cc  \
				-O3 --x-assign fast --x-initial fast --noassert  \
				--trace

default: verilog $(BIN)

$(shell mkdir -p $(BUILD_DIR))

SRC_AUTO_BIND = $(abspath $(BUILD_DIR)/auto_bind.cpp)
$(SRC_AUTO_BIND): $(NXDC_FILES)
	python3 $(NVBOARD_HOME)/scripts/auto_pin_bind.py $^ $@

# NVBoard interop: source
VSRCS = $(shell find $(abspath ./build) -regextype posix-extended -regex "^.*\.(sv|v)$$")
CSRCS = $(shell find $(abspath ./csrc) -regextype posix-extended -regex "^.*\.(c|cc|cpp)$$")
CSRCS += $(SRC_AUTO_BIND)

# NVBoard interop: rules
include $(NVBOARD_HOME)/scripts/nvboard.mk

# NVBoard interop: verilator flags
INCFLAGS = $(addprefix -I, $(INC_PATH))
CXXFLAGS += $(INCFLAGS)
CXXFLAGS += -DTOP_NAME="\"V$(TOP_NAME)\"" -O3

$(BIN): $(VSRCS) $(CSRCS) $(NVBOARD_ARCHIVE)
	./script/remove_f.py $(VSRCS)
	@rm -rf $(OBJ_DIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) \
		--top-module $(TOP_NAME) $^ \
		$(addprefix -CFLAGS , $(CXXFLAGS)) $(addprefix -LDFLAGS , $(LDFLAGS)) \
		--Mdir $(OBJ_DIR) --exe -o $(abspath $(BIN))

test:
	mill -i $(PRJ_NAME).test

verilog:
	$(call git_commit, "generate verilog")
	mkdir -p $(BUILD_DIR)
	mill -i $(PRJ_NAME).runMain Elaborate --target-dir $(BUILD_DIR)

help:
	mill -i $(PRJ_NAME).runMain Elaborate --help

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

clean:
	-rm -rf $(BUILD_DIR)

sim: $(BIN)
	$(call git_commit, "sim RTL")
	@$(BIN)

wave:
	gtkwave --giga dump.vcd

all: default

.PHONY: default all test verilog help reformat checkformat clean sim wave

-include ../Makefile
