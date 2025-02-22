package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class IfuExcp extends Bundle {
  val misalign = Bool()
  val access   = Bool()
}

class Ifu2IduMsg extends Bundle {
  // GEN
  // Used by Idu
  val instr = Output(UInt(32.W))
  // Unused by Idu
  val pc      = Output(UInt(XLen.W))
  val snpc    = Output(UInt(XLen.W))
  val pdnpc   = Output(UInt(XLen.W))
  val ifuExcp = Output(new IfuExcp)
  // PASS-THRU
  // (none)
}

class Ifu extends Module {
  class Port extends Bundle {
    val ctrl = new PipelineCtrl {
      val stall = Input(Bool())
      val flush = Input(Bool())
    }
    val dnpc   = Input(UInt(XLen.W))
    val msgOut = Decoupled(new Ifu2IduMsg)
    val rReq   = Irrevocable(new MemReadReq(XLen.W))
    val rResp  = Flipped(Irrevocable(new MemReadResp(32.W)))
    val idle   = Output(Bool())
  }
  val io = IO(new Port)

  private val pdnpc = Wire(UInt(XLen.W))
  private val dnpc  = Wire(UInt(XLen.W))
  private val fetch = Wire(Bool())

  private val flush   = RegInit(false.B)
  private val flushPc = RegEnable(io.dnpc, io.ctrl.flush)
  private val pc      = RegEnable(dnpc, InitPCVal.U(XLen.W), fetch)

  private val snpc = pc + 4.U

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
      S_ReadReq -> MuxDontTouch(io.rReq.ready, S_Read, S_ReadReq),
      S_Read    -> MuxDontTouch(io.rResp.valid, S_Done, S_Read),
      S_Done    -> MuxDontTouch(flush | io.msgOut.ready, S_Idle, S_Done)
    )
  )

  pdnpc := snpc
  dnpc  := Mux(flush, flushPc, pdnpc)
  fetch := y === S_Idle & ~io.ctrl.stall

  flush := MuxCase(
    flush,
    Seq(
      (y === S_Idle) -> false.B,
      io.ctrl.flush  -> true.B
    )
  )

  private val instr = RegEnable(io.rResp.bits.data, io.rResp.valid)
  private val rResp = RegEnable(io.rResp.bits.rResp, io.rResp.valid)

  io.rReq.bits.addr := pc
  io.rReq.bits.size := AxSize.Bytes4.U
  io.rReq.valid     := y === S_ReadReq
  io.rResp.ready    := y === S_Done

  io.msgOut.bits.instr := instr
  io.msgOut.bits.pc    := pc
  io.msgOut.bits.snpc  := snpc
  io.msgOut.bits.pdnpc := pdnpc

  io.msgOut.bits.ifuExcp.misalign := dnpc(1, 0) =/= 0.U
  io.msgOut.bits.ifuExcp.access   := rResp =/= RResp.Okay

  io.msgOut.valid := y === S_Done & ~flush

  io.idle := y === S_Idle
}
