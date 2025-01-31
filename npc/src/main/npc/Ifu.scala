package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class Ifu2IduMsg extends Bundle {
  // GEN
  // Used by Idu
  val instr = Output(UInt(32.W))
  val bad   = Output(Bool())
  // Unused by Idu
  val pc   = Output(UInt(XLen.W))
  val snpc = Output(UInt(XLen.W))
  // PASS-THRU
  // (none)
}

class Ifu extends Module {
  class Port extends Bundle {
    val stall  = Input(Bool())
    val msgIn  = Flipped(new Pcu2IfuMsg)
    val msgOut = Decoupled(new Ifu2IduMsg)
    val rReq   = Irrevocable(new MemReadReq(XLen.W))
    val rResp  = Flipped(Irrevocable(new MemReadResp(32.W)))
  }
  val io = IO(new Port)

  private val fetchNext = Wire(Bool())
  private val pc = RegEnable(io.msgIn.pcf, InitPCVal.U(XLen.W), fetchNext)

  // PC does not align to 4 bytes
  private val pcBad = ~(io.msgIn.pcf(1, 0) === 0.U)

  private object State extends CvtChiselEnum {
    val S_Idle    = Value
    val S_ReadReq = Value
    val S_Read    = Value
    val S_Done    = Value
  }
  import State._
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle    -> S_ReadReq,
      S_ReadReq -> Mux(io.rReq.ready, S_Read, S_ReadReq),
      S_Read    -> Mux(io.rResp.valid, S_Done, S_Read),
      S_Done    -> Mux(io.msgOut.ready, S_Idle, S_Done)
    )
  )

  fetchNext := y === S_Idle & ~io.stall

  private val instr = RegEnable(io.rResp.bits.data, io.rResp.valid)

  io.rReq.bits.addr := pc
  io.rReq.bits.size := AxSize.Bytes4.U
  io.rReq.valid     := y === S_ReadReq
  io.rResp.ready    := y === S_Done

  io.msgOut.bits.bad   := (~reset.asBool) & pcBad
  io.msgOut.bits.instr := instr
  io.msgOut.bits.pc    := pc
  io.msgOut.bits.snpc  := pc + 4.U

  io.msgOut.valid := y === S_Done
}
