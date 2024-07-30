package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class Ifu2IduMsg extends Bundle {
  val instr = Output(UInt(32.W))
  // Pass-through for Idu
  val pc = Output(UInt(XLen.W))
}

class IfuIO extends Bundle {
  val msgIn  = Flipped(Decoupled(new PcUpdate2IfuMsg))
  val msgOut = Decoupled(new Ifu2IduMsg)
}

class Ifu extends Module {
  val io = IO(new IfuIO)

  val pc = RegEnable(io.msgIn.bits.dnpc, InitPCVal.U(XLen.W), io.msgIn.valid)

  val port = Module(new SramRPort(XLen.W, 32.W))

  object State extends CvtChiselEnum {
    val S_Idle      = Value
    val S_WaitReady = Value
  }
  import State._
  val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle      -> Mux(port.io.done, S_WaitReady, S_Idle),
      S_WaitReady -> Mux(io.msgOut.ready, S_Idle, S_WaitReady)
    )
  )

  port.io.rAddr := pc
  port.io.rEn   := ~reset.asBool & (y === S_Idle)

  io.msgOut.bits.instr := port.io.rData
  io.msgOut.bits.pc    := pc

  io.msgIn.ready  := y === S_WaitReady
  io.msgOut.valid := y === S_WaitReady
}
