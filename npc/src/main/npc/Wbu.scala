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

class Wbu extends Module {
  class Port extends Bundle {
    val msgIn    = Flipped(Decoupled(new Lsu2WbuMsg))
    val msgOut   = Decoupled(new Bundle {})
    val gprWrite = Flipped(new GprFileWriteConn)
    val pc       = Output(UInt(XLen.W))
    val instr    = Output(UInt(XLen.W))
    val retired  = Output(Bool())
    val break    = Output(Bool())
    val bad      = Output(Bool())
  }
  val io = IO(new Port)

  import WbSel._

  private val dataAlu  = io.msgIn.bits.d
  private val dataSnpc = io.msgIn.bits.snpc
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

  io.gprWrite.wEn    := io.msgIn.valid & ~io.msgIn.bits.bad & io.msgIn.bits.wbEn
  io.gprWrite.rdIdx  := io.msgIn.bits.rdIdx
  io.gprWrite.rdData := wbData

  io.msgIn.ready  := io.msgOut.ready
  io.msgOut.valid := io.msgIn.valid

  io.break   := io.msgIn.bits.break
  io.instr   := io.msgIn.bits.instr
  io.pc      := io.msgIn.bits.pc
  io.retired := io.msgIn.valid
  io.bad     := io.msgIn.valid & io.msgIn.bits.bad
}
