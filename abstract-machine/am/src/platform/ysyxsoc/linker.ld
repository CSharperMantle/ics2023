ENTRY(_start)

PHDRS {
  text PT_LOAD;
  data PT_LOAD;
}

MEMORY {
  sram  : ORIGIN = 0x0f000000, LENGTH = 8K
  mrom  : ORIGIN = 0x20000000, LENGTH = 4K
  flash : ORIGIN = 0x30000000, LENGTH = 16M
}

SECTIONS {
  _sram_start = ORIGIN(sram);
  _sram_end = _sram_start + LENGTH(sram);
  _mrom_start = ORIGIN(mrom);
  _mrom_end = _mrom_start + LENGTH(mrom);
  _flash_start = ORIGIN(flash);
  _flash_end = _flash_start + LENGTH(flash);

  . = _flash_start;

  .text : {
    *(entry)
    *(.text*)
  } > flash AT> flash : text
  etext = .;
  _etext = .;

  .rodata : {
    *(.rodata*)
    *(.srodata*)
  } > flash AT> flash

  .data : {
    *(.data*)
    *(.sdata*)
  } > sram AT> flash : data

  _data = ADDR(.data);
  edata = _data + SIZEOF(.data);
  _edata = _data + SIZEOF(.data);

  _data_load = LOADADDR(.data);
  _edata_load = _data_load + SIZEOF(.data);

  .bss : {
    *(.bss*)
    *(.sbss*)
    *(.scommon)
  } > sram AT> flash
  end = .;
  _end = .;

  _bss_start = ADDR(.bss);
  _ebss = _bss_start + SIZEOF(.bss);

  _stack_top = .;
  . += _stack_size;
  _stack_pointer = .;

  _heap_start = .;
}
