package npc.common

import chisel3._
import chisel3.util._

class CounterNIO(N: Width) extends Bundle {
  val i_en = Input(Bool())
  val o_q  = Output(UInt(N))
}

class CounterN(N: Width) extends Module {
  val io = IO(new CounterNIO(N))

  val counter = RegInit(0.U(N))

  counter := Mux(io.i_en, counter + 1.U, counter)

  io.o_q := counter
}
