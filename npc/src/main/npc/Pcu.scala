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

class Pcu2IfuMsg extends Bundle {
  val pcf = Output(UInt(XLen.W))
}

class Pcu extends Module {
  class Port extends Bundle {
    val pc      = Input(UInt(XLen.W))
    val snpc    = Input(UInt(XLen.W))
    val pcSel   = Input(PcSelField.chiselType)
    val brTaken = Input(Bool())
    val imm     = Input(UInt(XLen.W))
    val d       = Input(UInt(XLen.W))
    val mepc    = Input(UInt(XLen.W))
    val mtvec   = Input(UInt(XLen.W))
    val msgOut  = new Pcu2IfuMsg
  }
  val io = IO(new Port)

  private val dnpc: UInt = MuxLookup(io.pcSel, 0.U)(
    Seq(
      PcSel.PcSnpc  -> io.snpc,
      PcSel.PcAlu   -> io.d,
      PcSel.PcBr    -> Mux(io.brTaken, io.pc + io.imm, io.snpc),
      PcSel.PcMepc  -> io.mepc,
      PcSel.PcMtvec -> io.mtvec
    )
  )

  io.msgOut.pcf := dnpc
}
