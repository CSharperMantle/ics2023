package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class PcUpdate2IfuMsg extends Bundle {
  val pc    = Output(UInt(XLen.W))
  val dnpc  = Output(UInt(XLen.W))
  val inval = Output(Bool())
}

class PcUpdateIO extends Bundle {
  val msgIn  = Flipped(Irrevocable(new Wbu2PcUpdateMsg))
  val msgOut = Irrevocable(new PcUpdate2IfuMsg)
}

class PcUpdate extends Module {
  val io = IO(new PcUpdateIO)

  val snpc     = io.msgIn.bits.pc + 4.U
  val brTarget = Mux(io.msgIn.bits.brTaken, io.msgIn.bits.pc + io.msgIn.bits.imm, snpc)
  val pcSelDec = Decoder1H(
    Seq(
      InstrPcSel.PcSnpc.BP  -> 0,
      InstrPcSel.PcAlu.BP   -> 1,
      InstrPcSel.PcBr.BP    -> 2,
      InstrPcSel.PcMepc.BP  -> 3,
      InstrPcSel.PcMtvec.BP -> 4
    )
  )
  val pcSel1H = pcSelDec(io.msgIn.bits.pcSel)
  val dnpc: UInt = Mux1H(
    Seq(
      pcSel1H(0) -> snpc,
      pcSel1H(1) -> io.msgIn.bits.d,
      pcSel1H(2) -> brTarget,
      pcSel1H(3) -> io.msgIn.bits.mepc,
      pcSel1H(4) -> io.msgIn.bits.mtvec,
      pcSel1H(5) -> 0.U
    )
  )

  io.msgOut.bits.pc    := io.msgIn.bits.pc
  io.msgOut.bits.dnpc  := dnpc
  io.msgOut.bits.inval := io.msgIn.bits.inval | pcSel1H(pcSelDec.bitBad)

  io.msgIn.ready  := io.msgOut.ready
  io.msgOut.valid := io.msgIn.valid
}
