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

class Wbu2PcuMsg extends Bundle {
  val pc      = Output(UInt(XLen.W))
  val pcSel   = Output(PcSelField.chiselType)
  val brTaken = Output(Bool())
  val imm     = Output(UInt(XLen.W))
  val d       = Output(UInt(XLen.W))
  val mepc    = Output(UInt(XLen.W))
  val mtvec   = Output(UInt(XLen.W))
  val bad     = Output(Bool())
}

class Wbu extends Module {
  class Port extends Bundle {
    val msgIn  = Flipped(Irrevocable(new Lsu2WbuMsg))
    val msgOut = Irrevocable(new Wbu2PcuMsg)

    val gprWrite = Flipped(new GprFileWriteConn)
  }
  val io = IO(new Port)

  import WbSel._

  private val bad = io.msgIn.bits.bad & ~io.msgIn.bits.bad

  private val dataAlu  = io.msgIn.bits.d
  private val dataSnpc = io.msgIn.bits.pc + 4.U
  private val dataMem  = io.msgIn.bits.memRData
  private val dataCsr  = io.msgIn.bits.csrVal

  private val wbData = MuxLookup(io.msgIn.bits.wbSel, 0.U)(
    Seq(
      WbSel.WbAlu  -> dataAlu,
      WbSel.WbSnpc -> dataSnpc,
      WbSel.WbMem  -> dataMem,
      WbSel.WbCsr  -> dataCsr
    )
  )

  io.gprWrite.wEn    := io.msgIn.valid & ~bad & io.msgIn.bits.wbEn
  io.gprWrite.rdIdx  := io.msgIn.bits.rdIdx
  io.gprWrite.rdData := wbData

  io.msgOut.bits.pc      := io.msgIn.bits.pc
  io.msgOut.bits.pcSel   := io.msgIn.bits.pcSel
  io.msgOut.bits.brTaken := io.msgIn.bits.brTaken
  io.msgOut.bits.imm     := io.msgIn.bits.imm
  io.msgOut.bits.d       := io.msgIn.bits.d
  io.msgOut.bits.mepc    := io.msgIn.bits.mepc
  io.msgOut.bits.mtvec   := io.msgIn.bits.mtvec
  io.msgOut.bits.bad     := io.msgIn.bits.bad

  io.msgIn.ready  := io.msgOut.ready
  io.msgOut.valid := io.msgIn.valid
}
