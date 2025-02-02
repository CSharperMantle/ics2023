package npc

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

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
  private val rs1 = Mux(io.msgIn.bits.fwdEn.rs1, io.msgIn.bits.fwdRegVal, io.gprRead.rs1)
  private val rs2 = Mux(io.msgIn.bits.fwdEn.rs2, io.msgIn.bits.fwdRegVal, io.gprRead.rs2)
  private val csrNop = (
    io.msgIn.bits.csrOp === CsrOp.Unk
      & io.msgIn.bits.excpAdj === CsrExcpAdj.ExcpAdjNone
  )
  private val gprNop = (
    io.msgIn.bits.srcASel =/= ExSrcASel.SrcARs1
      & io.msgIn.bits.srcBSel =/= ExSrcBSel.SrcBRs2
  )

  private val alu = Module(new Alu)

  private val srcA = MuxLookup(io.msgIn.bits.srcASel, 0.U)(
    Seq(
      SrcARs1  -> rs1,
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
  io.csrConn.csrOp   := io.msgIn.bits.csrOp
  io.csrConn.excpAdj := io.msgIn.bits.excpAdj
  io.csrConn.pc      := io.msgIn.bits.pc

  io.msgOut.bits.d   := alu.io.d
  io.msgOut.bits.rs2 := rs2
  io.msgOut.bits.bad := bad

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
    val S_Idle = Value
    val S_Gpr  = Value
    val S_Csr  = Value
    val S_Done = Value
  }
  import State._
  private val (firstAction, _) = State.safe(
    decoder(
      Cat(bad, gprNop, csrNop),
      TruthTable(
        Seq(
          "b1??".BP -> S_Done.BP,
          "b011".BP -> S_Done.BP,
          "b00?".BP -> S_Gpr.BP,
          "b010".BP -> S_Csr.BP
        ),
        S_Idle.BP
      )
    )
  )
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle -> Mux(io.msgIn.valid, firstAction, S_Idle),
      S_Gpr  -> Mux(io.gprRead.ready, Mux(csrNop, S_Done, S_Csr), S_Gpr),
      S_Csr  -> Mux(io.csrConn.ready, S_Done, S_Csr),
      S_Done -> Mux(io.msgOut.ready, S_Idle, S_Done)
    )
  )

  io.gprRead.valid := y === S_Gpr
  io.csrConn.valid := y === S_Csr

  io.msgIn.ready  := y === S_Idle & ~io.msgIn.valid
  io.msgOut.valid := y === S_Done
}
