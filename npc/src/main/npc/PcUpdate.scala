package npc

import chisel3._
import chisel3.util._

import common._
import npc._

object PcSel extends CvtChiselEnum {
  val PcSnpc  = Value
  val PcAlu   = Value
  val PcBr    = Value
  val PcMepc  = Value
  val PcMtvec = Value
}

class PcUpdate2IfuMsg extends Bundle {
  val pc   = Output(UInt(XLen.W))
  val dnpc = Output(UInt(XLen.W))
  val bad  = Output(Bool())
}

class PcUpdate extends Module {
  class Port extends Bundle {
    val msgIn  = Flipped(Irrevocable(new Wbu2PcUpdateMsg))
    val msgOut = Irrevocable(new PcUpdate2IfuMsg)
  }
  val io = IO(new Port)

  private val snpc     = io.msgIn.bits.pc + 4.U
  private val brTarget = Mux(io.msgIn.bits.brTaken, io.msgIn.bits.pc + io.msgIn.bits.imm, snpc)
  private val dnpc: UInt = MuxLookup(io.msgIn.bits.pcSel, 0.U)(
    Seq(
      PcSel.PcSnpc  -> snpc,
      PcSel.PcAlu   -> io.msgIn.bits.d,
      PcSel.PcBr    -> brTarget,
      PcSel.PcMepc  -> io.msgIn.bits.mepc,
      PcSel.PcMtvec -> io.msgIn.bits.mtvec
    )
  )

  io.msgOut.bits.pc   := io.msgIn.bits.pc
  io.msgOut.bits.dnpc := dnpc
  io.msgOut.bits.bad  := io.msgIn.bits.bad

  io.msgIn.ready  := io.msgOut.ready
  io.msgOut.valid := io.msgIn.valid
}
