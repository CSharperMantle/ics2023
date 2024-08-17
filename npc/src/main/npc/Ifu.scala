package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class Ifu2IduMsg extends Bundle {
  val instr = Output(UInt(32.W))
  val bad   = Output(Bool())
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

  private val canUpdatePc = io.msgIn.valid & ~io.msgIn.bits.bad

  private val pc = RegEnable(io.msgIn.bits.dnpc, InitPCVal.U(XLen.W), canUpdatePc)

  // PC does not align to 4 bytes
  private val pcBad = ~(pc(1, 0) === 0.U)

  private object State extends CvtChiselEnum {
    val S_Idle      = Value
    val S_ReadReq   = Value
    val S_Read      = Value
    val S_Wait4Next = Value
  }
  import State._
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle      -> Mux(pcBad, S_Wait4Next, S_ReadReq),
      S_ReadReq   -> Mux(io.rReq.ready, S_Read, S_ReadReq),
      S_Read      -> Mux(io.rResp.valid, S_Wait4Next, S_Read),
      S_Wait4Next -> Mux(io.msgOut.ready, S_ReadReq, S_Wait4Next)
    )
  )

  private val instr = RegEnable(io.rResp.bits.data, io.rResp.valid)

  io.rReq.bits.addr := pc
  io.rReq.bits.size := AxSize.Bytes4.U
  io.rReq.valid     := y === S_ReadReq
  io.rResp.ready    := y === S_Wait4Next

  io.msgOut.bits.bad   := (~reset.asBool) & pcBad
  io.msgOut.bits.instr := instr
  io.msgOut.bits.pc    := pc

  io.msgIn.ready  := y === S_Wait4Next
  io.msgOut.valid := y === S_Wait4Next
}
