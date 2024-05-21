package top

import chisel3._
import chisel3.util._

class CounterN(N: Width) extends Module {
  val io = IO(new Bundle {
    val en = Input(Bool())
    val q  = Output(UInt(N))
  })

  val counter = RegInit(0.U(N))

  counter := Mux(io.en, counter + 1.U, counter)

  io.q := counter
}
