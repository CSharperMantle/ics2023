set CLK_PORT_NAME clock
set CLK_FREQ_MHZ 500
if {[info exists env(CLK_FREQ_MHZ)]} {
  set CLK_FREQ_MHZ $::env(CLK_FREQ_MHZ)
} else {
  puts "W: Env var CLK_FREQ_MHZ is not defined. Use $CLK_FREQ_MHZ MHz by default"
}
set clk_io_pct 0.2
set CLK_PORT [get_ports $CLK_PORT_NAME]
create_clock -name core_clock -period [expr 1000.0 / $CLK_FREQ_MHZ] $CLK_PORT
