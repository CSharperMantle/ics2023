ENTRY(_start)

PHDRS {
  text PT_LOAD;
  data PT_LOAD;
}

MEMORY {
  sram : ORIGIN = 0x0f000000, LENGTH = 8K
  mrom : ORIGIN = 0x20000000, LENGTH = 4K
}

SECTIONS {
  _sram_start = ORIGIN(sram);
  _sram_end = _sram_start + LENGTH(sram);
  _mrom_start = ORIGIN(mrom);
  _mrom_end = _mrom_start + LENGTH(mrom);

  . = _mrom_start;

  .text : {
    *(entry)
    *(.text*)
  } > mrom AT> mrom : text
  etext = .;
  _etext = .;

  .rodata : {
    *(.rodata*)
    *(.srodata*)
  } > mrom AT> mrom

  .data : {
    *(.data*)
    *(.sdata*)
  } > sram AT> mrom : data

  _data = ADDR(.data);
  edata = _data + SIZEOF(.data);
  _edata = _data + SIZEOF(.data);

  _data_load = LOADADDR(.data);
  _edata_load = _data_load + SIZEOF(.data);

  .bss : {
  _bss_start = .;
    *(.bss*)
    *(.sbss*)
    *(.scommon)
  } > sram AT> mrom
  end = .;
  _end = .;

  _stack_top = .;
  . += _stack_size;
  _stack_pointer = .;

  _heap_start = .;
}
