package npc

import chisel3._
import chisel3.util._

import common._
import npc._

object WbSel extends CvtChiselEnum {
  val WbAlu  = Value
  val WbSnpc = Value
  val WbMem  = Value
  val WbCsr  = Value
}

class Wbu2PcUpdateMsg extends Bundle {
  val pc      = Output(UInt(XLen.W))
  val pcSel   = Output(PcSelField.chiselType)
  val brTaken = Output(Bool())
  val imm     = Output(UInt(XLen.W))
  val d       = Output(UInt(XLen.W))
  val mepc    = Output(UInt(XLen.W))
  val mtvec   = Output(UInt(XLen.W))
  val inval   = Output(Bool())
}

class WbuIO extends Bundle {
  val msgIn  = Flipped(Decoupled(new Lsu2WbuMsg))
  val msgOut = Decoupled(new Wbu2PcUpdateMsg)

  val gprWrite = Flipped(new GprFileWriteConn)
}

class Wbu extends Module {
  import WbSel._

  val io = IO(new WbuIO)

  val dataAlu  = io.msgIn.bits.d
  val dataSnpc = io.msgIn.bits.pc + 4.U
  val dataMem  = io.msgIn.bits.memRData
  val dataCsr  = io.msgIn.bits.csrVal

  val wbSelDec = Decoder1H(
    Seq(
      WbSel.WbAlu.BP  -> 0,
      WbSel.WbSnpc.BP -> 1,
      WbSel.WbMem.BP  -> 2,
      WbSel.WbCsr.BP  -> 3
    )
  )
  val wbSel1H = wbSelDec(io.msgIn.bits.wbSel)
  val wbData = Mux1H(
    Seq(
      wbSel1H(0) -> dataAlu,
      wbSel1H(1) -> dataSnpc,
      wbSel1H(2) -> dataMem,
      wbSel1H(3) -> dataCsr,
      wbSel1H(4) -> 0.U
    )
  )

  io.gprWrite.wEn    := io.msgIn.ready & io.msgIn.bits.wbEn
  io.gprWrite.rdIdx  := io.msgIn.bits.rdIdx
  io.gprWrite.rdData := wbData

  io.msgOut.bits.pc      := io.msgIn.bits.pc
  io.msgOut.bits.pcSel   := io.msgIn.bits.pcSel
  io.msgOut.bits.brTaken := io.msgIn.bits.brTaken
  io.msgOut.bits.imm     := io.msgIn.bits.imm
  io.msgOut.bits.d       := io.msgIn.bits.d
  io.msgOut.bits.mepc    := io.msgIn.bits.mepc
  io.msgOut.bits.mtvec   := io.msgIn.bits.mtvec
  io.msgOut.bits.inval   := io.msgIn.bits.inval | wbSel1H(wbSelDec.bitBad)

  io.msgIn.ready  := io.msgOut.ready
  io.msgOut.valid := io.msgIn.valid
}
