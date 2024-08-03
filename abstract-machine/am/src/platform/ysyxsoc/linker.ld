ENTRY(_start)
PHDRS {
  text PT_LOAD;
  data PT_LOAD;
}

SECTIONS {
  . = _sram_start;
  . = ALIGN(0x1000);
  _stack_top = .;
  . += _stack_size;
  _stack_pointer = .;

  _heap_start = ALIGN(0x1000);

  . = _mrom_start + _entry_offset;
  .text : {
    *(entry)
    *(.text*)
  } : text
  etext = .;
  _etext = .;

  .rodata : {
    *(.rodata*)
  }

  .data : {
    *(.data)
  } : data
  edata = .;
  _data = .;

  .bss : {
  _bss_start = .;
    *(.bss*)
    *(.sbss*)
    *(.scommon)
  }
  end = .;
  _end = .;
}
