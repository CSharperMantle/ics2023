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
  // GEN
  // Used by Lsu
  val d   = Output(UInt(XLen.W))
  val rs2 = Output(UInt(XLen.W))
  val bad = Output(Bool())
  // Unused by Lsu
  val brTaken = Output(Bool())
  val csrVal  = Output(UInt(XLen.W))
  val mepc    = Output(UInt(XLen.W))
  val mtvec   = Output(UInt(XLen.W))
  // PASS-THRU
  val instr     = Output(UInt(XLen.W))
  val memAction = Output(MemActionField.chiselType)
  val memWidth  = Output(MemWidthField.chiselType)
  val pc        = Output(UInt(XLen.W))
  val snpc      = Output(UInt(XLen.W))
  val pdnpc     = Output(UInt(XLen.W))
  val wbEn      = Output(WbEnField.chiselType)
  val wbSel     = Output(WbSelField.chiselType)
  val rdIdx     = Output(UInt(5.W))
  val pcSel     = Output(PcSelField.chiselType)
  val imm       = Output(UInt(XLen.W))
  val break     = Output(Bool())
}

class Exu extends Module {
  class Port extends Bundle {
    val msgIn  = Flipped(Decoupled(new Idu2ExuMsg))
    val msgOut = Decoupled(new Exu2LsuMsg)

    val gprRead = Flipped(new GprFileReadConn)
    val csrConn = Flipped(new CsrFileConn)
  }
  val io = IO(new Port)

  import ExSrcASel._
  import ExSrcBSel._

  private val bad = io.msgIn.bits.bad

  private val alu = Module(new Alu)

  private val srcA = MuxLookup(io.msgIn.bits.srcASel, 0.U)(
    Seq(
      SrcARs1  -> io.gprRead.rs1,
      SrcAPc   -> io.msgIn.bits.pc,
      SrcAR0   -> 0.U,
      SrcAZimm -> Cat(Fill(XLen - 5, false.B), io.msgIn.bits.rs1Idx)
    )
  )

  private val srcB = MuxLookup(io.msgIn.bits.srcBSel, 0.U)(
    Seq(
      SrcBRs2 -> io.gprRead.rs2,
      SrcBImm -> io.msgIn.bits.imm
    )
  )

  alu.io.s1      := srcA
  alu.io.s2      := srcB
  alu.io.calcOp  := AluCalcOp(io.msgIn.bits.aluCalcOp)
  alu.io.calcDir := io.msgIn.bits.aluCalcDir
  alu.io.brCond  := io.msgIn.bits.aluBrCond

  io.gprRead.rs1Idx := io.msgIn.bits.rs1Idx
  io.gprRead.rs2Idx := io.msgIn.bits.rs2Idx

  io.csrConn.s1      := srcA
  io.csrConn.csrAddr := io.msgIn.bits.imm(11, 0)
  io.csrConn.csrOp   := Mux(~bad, io.msgIn.bits.csrOp, CsrOp.Unk)
  io.csrConn.excpAdj := Mux(~bad, io.msgIn.bits.excpAdj, CsrExcpAdj.ExcpAdjNone)
  io.csrConn.pc      := io.msgIn.bits.pc

  io.msgOut.bits.d   := alu.io.d
  io.msgOut.bits.rs2 := io.gprRead.rs2
  io.msgOut.bits.bad := io.msgIn.bits.bad

  io.msgOut.bits.brTaken := alu.io.brTaken
  io.msgOut.bits.csrVal  := io.csrConn.csrVal
  io.msgOut.bits.mepc    := io.csrConn.mepc
  io.msgOut.bits.mtvec   := io.csrConn.mtvec

  io.msgOut.bits.instr     := io.msgIn.bits.instr
  io.msgOut.bits.memAction := io.msgIn.bits.memAction
  io.msgOut.bits.memWidth  := io.msgIn.bits.memWidth
  io.msgOut.bits.pc        := io.msgIn.bits.pc
  io.msgOut.bits.snpc      := io.msgIn.bits.snpc
  io.msgOut.bits.pdnpc     := io.msgIn.bits.pdnpc
  io.msgOut.bits.wbEn      := io.msgIn.bits.wbEn
  io.msgOut.bits.wbSel     := io.msgIn.bits.wbSel
  io.msgOut.bits.rdIdx     := io.msgIn.bits.rdIdx
  io.msgOut.bits.pcSel     := io.msgIn.bits.pcSel
  io.msgOut.bits.imm       := io.msgIn.bits.imm
  io.msgOut.bits.break     := io.msgIn.bits.break

  private object State extends CvtChiselEnum {
    val S_Idle  = Value
    val S_RdReg = Value
    val S_Csr   = Value
    val S_Done  = Value
  }
  import State._
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle  -> Mux(io.msgIn.valid, Mux(io.msgIn.bits.bad, S_Done, S_RdReg), S_Idle),
      S_RdReg -> Mux(io.gprRead.ready, S_Csr, S_RdReg),
      S_Csr   -> Mux(io.csrConn.ready, S_Done, S_Csr),
      S_Done  -> Mux(io.msgOut.ready, S_Idle, S_Done)
    )
  )

  io.gprRead.valid := y === S_RdReg
  io.csrConn.valid := y === S_Csr

  io.msgIn.ready  := y === S_Idle & ~io.msgIn.valid
  io.msgOut.valid := y === S_Done
}
