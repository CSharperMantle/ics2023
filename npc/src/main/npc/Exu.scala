package npc

import chisel3._
import chisel3.util._

import common._
import npc._

object ExSrcASel extends CvtChiselEnum {
  val SrcARs1  = Value
  val SrcAPc   = Value
  val SrcAR0   = Value
  val SrcAZimm = Value
}

object ExSrcBSel extends CvtChiselEnum {
  val SrcBRs2 = Value
  val SrcBImm = Value
}

class Exu2LsuMsg extends Bundle {
  val memAction = Output(MemActionField.chiselType)
  val memWidth  = Output(MemWidthField.chiselType)
  val d         = Output(UInt(XLen.W))
  val rs2       = Output(UInt(XLen.W))
  val bad       = Output(Bool())
  // Pass-through for Lsu
  val pc      = Output(UInt(XLen.W))
  val wbEn    = Output(WbEnField.chiselType)
  val wbSel   = Output(WbSelField.chiselType)
  val csrVal  = Output(UInt(XLen.W))
  val rdIdx   = Output(UInt(5.W))
  val pcSel   = Output(PcSelField.chiselType)
  val brTaken = Output(Bool())
  val imm     = Output(UInt(XLen.W))
  val mepc    = Output(UInt(XLen.W))
  val mtvec   = Output(UInt(XLen.W))
}

class Exu extends Module {
  class Port extends Bundle {
    val msgIn  = Flipped(Irrevocable(new Idu2ExuMsg))
    val msgOut = Irrevocable(new Exu2LsuMsg)

    val gprRead = Flipped(new GprFileReadConn)
    val csrConn = Flipped(new CsrFileConn)
  }
  val io = IO(new Port)

  import ExSrcASel._
  import ExSrcBSel._

  private val bad = io.msgIn.valid & io.msgIn.bits.bad

  private val alu = Module(new Alu)

  private val srcASelDec = Decoder1H(
    Seq(
      SrcARs1.BP  -> 0,
      SrcAPc.BP   -> 1,
      SrcAR0.BP   -> 2,
      SrcAZimm.BP -> 3
    )
  )
  private val srcASel1H = srcASelDec(io.msgIn.bits.srcASel)
  private val srcA = Mux1H(
    Seq(
      srcASel1H(0) -> io.gprRead.rs1,
      srcASel1H(1) -> io.msgIn.bits.pc,
      srcASel1H(2) -> 0.U,
      srcASel1H(3) -> Cat(Fill(XLen - 5, false.B), io.msgIn.bits.rs1Idx),
      srcASel1H(4) -> 0.U
    )
  )

  private val srcBSelDec = Decoder1H(
    Seq(
      SrcBRs2.BP -> 0,
      SrcBImm.BP -> 1
    )
  )
  private val srcBSel1H = srcBSelDec(io.msgIn.bits.srcBSel)
  private val srcB = Mux1H(
    Seq(
      srcBSel1H(0) -> io.gprRead.rs2,
      srcBSel1H(1) -> io.msgIn.bits.imm,
      srcBSel1H(2) -> 0.U
    )
  )

  alu.io.s1      := srcA
  alu.io.s2      := srcB
  alu.io.calcOp  := io.msgIn.bits.aluCalcOp
  alu.io.calcDir := io.msgIn.bits.aluCalcDir
  alu.io.brCond  := io.msgIn.bits.aluBrCond

  io.gprRead.rs1Idx := io.msgIn.bits.rs1Idx
  io.gprRead.rs2Idx := io.msgIn.bits.rs2Idx

  io.csrConn.s1      := srcA
  io.csrConn.csrAddr := io.msgIn.bits.imm(11, 0)
  io.csrConn.csrOp   := Mux(io.msgIn.valid & ~bad, io.msgIn.bits.csrOp, CsrOp.Unk.U)
  io.csrConn.excpAdj := Mux(io.msgIn.valid & ~bad, io.msgIn.bits.excpAdj, CsrExcpAdj.ExcpAdjNone.U)
  io.csrConn.pc      := io.msgIn.bits.pc

  io.msgOut.bits.memAction := io.msgIn.bits.memAction
  io.msgOut.bits.memWidth  := io.msgIn.bits.memWidth
  io.msgOut.bits.d         := alu.io.d
  io.msgOut.bits.rs2       := io.gprRead.rs2
  io.msgOut.bits.bad := io.msgIn.bits.bad |
    srcASel1H(srcASelDec.bitBad) |
    srcBSel1H(srcBSelDec.bitBad)

  io.msgOut.bits.pc      := io.msgIn.bits.pc
  io.msgOut.bits.wbEn    := io.msgIn.bits.wbEn
  io.msgOut.bits.wbSel   := io.msgIn.bits.wbSel
  io.msgOut.bits.csrVal  := io.csrConn.csrVal
  io.msgOut.bits.rdIdx   := io.msgIn.bits.rdIdx
  io.msgOut.bits.pcSel   := io.msgIn.bits.pcSel
  io.msgOut.bits.brTaken := alu.io.brTaken
  io.msgOut.bits.imm     := io.msgIn.bits.imm
  io.msgOut.bits.mepc    := io.csrConn.mepc
  io.msgOut.bits.mtvec   := io.csrConn.mtvec

  io.msgIn.ready  := io.msgOut.ready
  io.msgOut.valid := io.msgIn.valid
}
