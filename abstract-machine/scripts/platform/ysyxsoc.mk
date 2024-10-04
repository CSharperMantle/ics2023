AM_SRCS := riscv/ysyxsoc/start.S \
           riscv/ysyxsoc/trm.c \
           riscv/ysyxsoc/cte.c \
           riscv/ysyxsoc/trap.S \
           riscv/ysyxsoc/ioe.c \
           platform/dummy/vme.c \
           platform/dummy/mpe.c

LDFLAGS += -T $(AM_HOME)/am/src/platform/ysyxsoc/linker.ld \
					--defsym=_stack_size=1K
LDFLAGS += --gc-sections -e _start

CFLAGS += -fdata-sections -ffunction-sections
CFLAGS += -DYSYXSOC
CFLAGS += -DMAINARGS=\"$(mainargs)\"
CFLAGS += -I$(AM_HOME)/am/src/platform/ysyxsoc/include

ASFLAGS += -DYSYXSOC

.PHONY: $(AM_HOME)/am/src/riscv/ysyxsoc/trm.c

image: $(IMAGE).elf
	@$(OBJDUMP) -d $(IMAGE).elf > $(IMAGE).txt
	@echo + OBJCOPY "->" $(IMAGE_REL).bin
	@$(OBJCOPY) -S --set-section-flags .bss=alloc -O binary $(IMAGE).elf $(IMAGE).bin

run: image
	$(MAKE) -C $(NPC_HOME) ISA=$(ISA) sim MAINARGS=$(IMAGE).bin
