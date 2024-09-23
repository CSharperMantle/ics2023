ENTRY(_start)

PHDRS {
  text PT_LOAD;
  data PT_LOAD;
}

MEMORY {
  sram  : ORIGIN = 0x0f000000, LENGTH = 8K
  mrom  : ORIGIN = 0x20000000, LENGTH = 4K
  flash : ORIGIN = 0x30000000, LENGTH = 16M
  psram : ORIGIN = 0x80000000, LENGTH = 4M
}

SECTIONS {
  _sram_start = ORIGIN(sram);
  _sram_end = _sram_start + LENGTH(sram);
  _mrom_start = ORIGIN(mrom);
  _mrom_end = _mrom_start + LENGTH(mrom);
  _flash_start = ORIGIN(flash);
  _flash_end = _flash_start + LENGTH(flash);
  _psram_start = ORIGIN(psram);
  _psram_end = _psram_start + LENGTH(psram);

  . = _flash_start;

  .text : {
    . = ALIGN(4);
    *(entry)
    *(.text*)
    etext = .;
    _etext = .;
  } > flash : text

  .rodata : {
    . = ALIGN(4);
    *(.rodata*)
    *(.srodata*)
  } > flash

  .data : {
    . = ALIGN(4);
    *(.data*)
    *(.sdata*)
  } > psram AT> flash : data

  _data = ADDR(.data);
  edata = _data + SIZEOF(.data);
  _edata = _data + SIZEOF(.data);
  _data_load = LOADADDR(.data);
  _edata_load = _data_load + SIZEOF(.data);

  _data_extra = DEFINED(_data_extra) ? _data_extra : 0;
  _edata_extra = DEFINED(_edata_extra) ? _edata_extra : 0;
  _data_extra_load = DEFINED(_data_extra_load) ? _data_extra_load : 0;
  _edata_extra_load = DEFINED(_edata_extra_load) ? _edata_extra_load : 0;

  .bss (NOLOAD) : {
    . = ALIGN(4);
    *(.bss*)
    *(.sbss*)
    *(.scommon)
  } > psram
  _bss_start = ADDR(.bss);
  _ebss = _bss_start + SIZEOF(.bss);

  _bss_extra_start = DEFINED(_bss_extra_start) ? _bss_extra_start : 0;
  _ebss_extra = DEFINED(_ebss_extra) ? _ebss_extra : 0;

  .stack (NOLOAD) : {
    . = ALIGN(8);
    _stack_top = .;
    . += _stack_size;
    . = ALIGN(8);
    _stack_pointer = .;
  } > psram

  . = ALIGN(8);
  end = .;
  _end = .;
  _heap_start = .;
}
