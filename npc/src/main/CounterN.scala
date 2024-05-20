package top

import chisel3._

class CounterN(N: Width) extends Module {
  val io = IO(new Bundle {
    val en = Input(Bool())
    val q  = Output(UInt(N))
  })

  val counter = RegInit(0.U(N))

  when(io.en) {
    counter := counter + 1.U
  }.otherwise {}

  io.q := counter
}
