BUILD_DIR = ./build

OBJ_DIR = $(BUILD_DIR)/obj_dir
BIN = $(BUILD_DIR)/$(TOP_NAME)

PRJ_NAME = playground


TOP_NAME = ysyxSoCFull
NXDC_FILE = constr/top.nxdc
SDC_FILE = constr/npc.sdc

INC_PATH ?= $(abspath ./include)

MAINARGS ?=

VERILATOR = verilator
VERILATOR_CFLAGS += -MMD --build -cc  \
				-O3 --x-assign fast --x-initial fast --noassert  \
				--trace-fst --timescale "1ns/1ns" --no-timing --autoflush  \
				-Wno-WIDTHEXPAND \
				--build-jobs 0

CONFIG_LLVM_CONFIG ?= llvm-config-15

default: verilog $(BIN)

$(shell mkdir -p $(BUILD_DIR))

SRC_AUTO_BIND = $(abspath $(BUILD_DIR)/auto_bind.cpp)
$(SRC_AUTO_BIND): $(NXDC_FILE)
	python3 $(NVBOARD_HOME)/scripts/auto_pin_bind.py $^ $@

# LLVM flags
CXXFLAGS += $(shell $(CONFIG_LLVM_CONFIG) --cxxflags) -fPIE
LDFLAGS += $(shell $(CONFIG_LLVM_CONFIG) --libs)

VSRCS = $(abspath $(BUILD_DIR)/ysyx_23060288.sv)
CSRCS = $(shell find $(abspath ./csrc) -regextype posix-extended -regex "^.*\.(c|cc|cpp)$$")
HEADERS = $(shell find $(INC_PATH) -regextype posix-extended -regex "^.*\.(h|hpp)$$")

SCALA_SRCS = $(shell find $(abspath ./src/main) -regextype posix-extended -regex "^.*\.scala$$")

# ysyxSoC
SOC_HOME ?= $(NPC_HOME)/../ysyxSoC
SOC_PERIPH_VSRCS = $(shell find $(abspath $(SOC_HOME)/perip) -regextype posix-extended -regex "^.*\.(sv|v)$$")
SOC_INC_PATHS = $(SOC_HOME)/perip/uart16550/rtl $(SOC_HOME)/perip/spi/rtl
SOC_INCFLAGS = $(addprefix -I, $(abspath $(SOC_INC_PATHS)))
SOC_TOP_VSRC = $(abspath $(SOC_HOME)/build/ysyxSoCFull.v)

# NVBoard interop: rules
include $(NVBOARD_HOME)/scripts/nvboard.mk
CSRCS += $(SRC_AUTO_BIND)

INCFLAGS = $(addprefix -I, $(INC_PATH))
CXXFLAGS += $(INCFLAGS)
CXXFLAGS += -DTOP_NAME="\"V$(TOP_NAME)\"" -g -std=gnu++14

$(VSRCS): $(SCALA_SRCS)
	$(call git_commit, "generate verilog")
	mkdir -p $(BUILD_DIR)
	mill -i $(PRJ_NAME).runMain Elaborate --target-dir $(BUILD_DIR)
	./script/remove_f.py $(VSRCS)

$(BIN): $(VSRCS) $(SOC_TOP_VSRC) $(SOC_PERIPH_VSRCS) $(CSRCS) $(NVBOARD_ARCHIVE) $(HEADERS)
	@rm -rf $(OBJ_DIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) \
		--top-module $(TOP_NAME) $(SOC_TOP_VSRC) $(SOC_PERIPH_VSRCS) $(VSRCS) $(CSRCS) $(NVBOARD_ARCHIVE) \
		$(addprefix -CFLAGS , $(CXXFLAGS)) $(addprefix -LDFLAGS , $(LDFLAGS)) \
		$(SOC_INCFLAGS) --Mdir $(OBJ_DIR) --exe -o $(abspath $(BIN))

test:
	mill -i $(PRJ_NAME).test

verilog: $(VSRCS)

help:
	mill -i $(PRJ_NAME).runMain Elaborate --help

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

clean:
	-rm -rf $(BUILD_DIR)

clean-all: clean
	-rm -rf out/

sim: $(BIN)
	$(call git_commit, "sim RTL")
	@$(BIN) $(MAINARGS)

wave:
	gtkwave --giga dump.fst

sta: $(VSRCS)
	make -C $(YOSYS_STA_HOME) sta DESIGN=$(STUID) SDC_FILE=$(abspath $(SDC_FILE)) \
		RTL_FILES=$(VSRCS) CLK_FREQ_MHZ=400

all: default

.PHONY: default all test verilog help reformat checkformat clean clean-all sim wave sta

-include ../Makefile
