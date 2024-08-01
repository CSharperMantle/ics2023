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
  val msgIn  = Flipped(Irrevocable(new PcUpdate2IfuMsg))
  val msgOut = Irrevocable(new Ifu2IduMsg)
  val rReq   = Irrevocable(new MemReadReq(XLen.W))
  val rResp  = Flipped(Irrevocable(new MemReadResp(32.W)))
}

class Ifu extends Module {
  val io = IO(new IfuIO)

  private val pc = RegEnable(io.msgIn.bits.dnpc, InitPCVal.U(XLen.W), io.msgIn.valid)

  private object State extends CvtChiselEnum {
    val S_Idle      = Value
    val S_Read      = Value
    val S_ReadDone  = Value
    val S_WaitReady = Value
  }
  import State._
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle      -> Mux(io.rReq.ready, S_Read, S_Idle),
      S_Read      -> Mux(io.rResp.valid, S_ReadDone, S_Read),
      S_ReadDone  -> S_WaitReady,
      S_WaitReady -> Mux(io.msgOut.ready, S_Idle, S_WaitReady)
    )
  )

  private val instr = RegEnable(io.rResp.bits.data, io.rResp.valid)

  io.rReq.bits.addr := pc
  io.rReq.valid     := ~reset.asBool & (y === S_Idle)
  io.rResp.ready    := y === S_ReadDone

  io.msgOut.bits.instr := instr
  io.msgOut.bits.pc    := pc

  io.msgIn.ready  := y === S_WaitReady
  io.msgOut.valid := y === S_WaitReady
}
