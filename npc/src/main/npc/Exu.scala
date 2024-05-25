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

class ExuIO extends Bundle {
  val srcASel = Input(UInt(ExSrcASel.W))
  val srcBSel = Input(UInt(ExSrcBSel.W))
  val calcOp  = Input(UInt(AluCalcOp.W))
  val calcDir = Input(UInt(AluCalcDir.W))
  val brCond  = Input(UInt(AluBrCond.W))
  val rs1Idx  = Input(UInt(5.W))
  val rs1     = Input(UInt(XLen.W))
  val rs2     = Input(UInt(XLen.W))
  val pc      = Input(UInt(XLen.W))
  val imm     = Input(UInt(XLen.W))
  val d       = Output(UInt(XLen.W))
  val brTaken = Output(Bool())
  val csrS1   = Output(UInt(XLen.W))
  val inval   = Output(Bool())
}

class Exu extends Module {
  import ExSrcASel._
  import ExSrcBSel._

  val io = IO(new ExuIO)

  val alu = Module(new Alu)

  val srcASelDec = Decoder1H(
    Seq(
      SrcARs1.BP  -> 0,
      SrcAPc.BP   -> 1,
      SrcAR0.BP   -> 2,
      SrcAZimm.BP -> 3
    )
  )
  val srcASel1H = srcASelDec(io.srcASel)
  val srcA = Mux1H(
    Seq(
      srcASel1H(0) -> io.rs1,
      srcASel1H(1) -> io.pc,
      srcASel1H(2) -> 0.U,
      srcASel1H(3) -> Cat(Fill(XLen - 5, false.B), io.rs1Idx),
      srcASel1H(4) -> 0.U
    )
  )

  val srcBSelDec = Decoder1H(
    Seq(
      SrcBRs2.BP -> 0,
      SrcBImm.BP -> 1
    )
  )
  val srcBSel1H = srcBSelDec(io.srcBSel)
  val srcB = Mux1H(
    Seq(
      srcBSel1H(0) -> io.rs2,
      srcBSel1H(1) -> io.imm,
      srcBSel1H(2) -> 0.U
    )
  )

  alu.io.s1      := srcA
  alu.io.s2      := srcB
  alu.io.calcOp  := io.calcOp
  alu.io.calcDir := io.calcDir
  alu.io.brCond  := io.brCond

  io.d       := alu.io.d
  io.brTaken := alu.io.brTaken
  io.csrS1   := srcA
  io.inval   := alu.io.brInvalid | srcASel1H(srcASelDec.bitBad) | srcBSel1H(srcBSelDec.bitBad)
}
