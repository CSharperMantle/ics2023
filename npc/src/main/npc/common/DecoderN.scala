package npc.common

import chisel3._
import chisel3.util._

class DecoderNIO(N: Width) extends Bundle {
  val i_x = Input(UInt(N))
  val o_y = Output(UInt((1 << N.get).W))
}

class DecoderN(N: Width) extends Module {
  val io = IO(new DecoderNIO(N))

  io.o_y := MuxLookup(io.i_x, 0.U)((0 until (1 << N.get)).map(i => i.U -> (BigInt(1) << i).U))
}
