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
    val S_Read      = Value
    val S_ReadDone  = Value
    val S_WaitReady = Value
  }
  import State._
  val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle      -> Mux(port.io.addr.ready, S_Read, S_Idle),
      S_Read      -> Mux(port.io.data.valid, S_ReadDone, S_Read),
      S_ReadDone  -> S_WaitReady,
      S_WaitReady -> Mux(io.msgOut.ready, S_Idle, S_WaitReady)
    )
  )

  val instr = RegEnable(port.io.data.bits.data, port.io.data.valid)

  port.io.addr.bits  := pc
  port.io.addr.valid := ~reset.asBool & (y === S_Idle)
  port.io.data.ready := y === S_ReadDone

  io.msgOut.bits.instr := instr
  io.msgOut.bits.pc    := pc

  io.msgIn.ready  := y === S_WaitReady
  io.msgOut.valid := y === S_WaitReady
}
