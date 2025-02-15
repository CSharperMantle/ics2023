package npc

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import common._
import npc._

object ExSrcASel extends CvtChiselEnum {
  val SrcARs1     = Value
  val SrcAPc      = Value
  val SrcAR0      = Value
  val SrcAZimm    = Value
  val SrcARs1Inv  = Value
  val SrcAZimmInv = Value
}

object ExSrcBSel extends CvtChiselEnum {
  val SrcBRs2  = Value
  val SrcBImm  = Value
  val SrcBCsr  = Value
  val SrcBZero = Value
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
  val csrAddr   = Output(UInt(12.W))
  val csrWbEn   = Output(CsrWbEnField.chiselType)
  val excpAdj   = Output(ExcpAdjField.chiselType)
  val pcSel     = Output(PcSelField.chiselType)
  val imm       = Output(UInt(XLen.W))
  val break     = Output(Bool())
}

class Exu extends Module {
  class Port extends Bundle {
    val msgIn  = Flipped(Decoupled(new Idu2ExuMsg))
    val msgOut = Decoupled(new Exu2LsuMsg)

    val gprRead = Flipped(new GprFileReadConn)
    val csrRead = Flipped(new CsrFileReadConn)
  }
  val io = IO(new Port)

  import ExSrcASel._
  import ExSrcBSel._

  private val bad = io.msgIn.bits.bad

  private val gprRespValid = Wire(Bool())
  private val rs1Read      = RegEnable(io.gprRead.rs1, gprRespValid)
  private val rs2Read      = RegEnable(io.gprRead.rs2, gprRespValid)
  private val csrRespValid = Wire(Bool())
  private val csrRead      = RegEnable(io.csrRead.csrVal, csrRespValid)
  private val mepcRead     = RegEnable(io.csrRead.mepc, csrRespValid)
  private val mtvecRead    = RegEnable(io.csrRead.mtvec, csrRespValid)

  private val rs1 = Mux(io.msgIn.bits.fwdEn.rs1, io.msgIn.bits.fwdGprVal, rs1Read)
  private val rs2 = Mux(io.msgIn.bits.fwdEn.rs2, io.msgIn.bits.fwdGprVal, rs2Read)
  private val csr = Mux(io.msgIn.bits.fwdEn.csr, io.msgIn.bits.fwdCsrVal, csrRead)

  private val csrNop = ~io.msgIn.bits.csrWbEn & io.msgIn.bits.excpAdj === CsrExcpAdj.ExcpAdjNone
  private val gprNop = (
    io.msgIn.bits.srcASel =/= ExSrcASel.SrcARs1
      & io.msgIn.bits.srcBSel =/= ExSrcBSel.SrcBRs2
  )

  private val alu = Module(new Alu)

  private val zimm = Cat(Fill(XLen - 5, false.B), io.msgIn.bits.rs1Idx)
  private val srcA = MuxLookup(io.msgIn.bits.srcASel, 0.U)(
    Seq(
      SrcARs1     -> rs1,
      SrcAPc      -> io.msgIn.bits.pc,
      SrcAR0      -> 0.U,
      SrcAZimm    -> zimm,
      SrcARs1Inv  -> ~rs1,
      SrcAZimmInv -> ~zimm
    )
  )

  private val srcB = MuxLookup(io.msgIn.bits.srcBSel, 0.U)(
    Seq(
      SrcBRs2  -> rs2,
      SrcBImm  -> io.msgIn.bits.imm,
      SrcBCsr  -> csr,
      SrcBZero -> 0.U
    )
  )

  alu.io.s1      := srcA
  alu.io.s2      := srcB
  alu.io.calcOp  := AluCalcOp(io.msgIn.bits.aluCalcOp)
  alu.io.calcDir := io.msgIn.bits.aluCalcDir
  alu.io.brCond  := io.msgIn.bits.aluBrCond

  io.gprRead.rs1Idx := io.msgIn.bits.rs1Idx
  io.gprRead.rs2Idx := io.msgIn.bits.rs2Idx

  io.csrRead.csrAddr := io.msgIn.bits.csrAddr

  io.msgOut.bits.d   := alu.io.d
  io.msgOut.bits.rs2 := rs2
  io.msgOut.bits.bad := bad

  io.msgOut.bits.brTaken := alu.io.brTaken
  io.msgOut.bits.csrVal  := csrRead
  io.msgOut.bits.mepc    := mepcRead
  io.msgOut.bits.mtvec   := mtvecRead

  io.msgOut.bits.instr     := io.msgIn.bits.instr
  io.msgOut.bits.memAction := io.msgIn.bits.memAction
  io.msgOut.bits.memWidth  := io.msgIn.bits.memWidth
  io.msgOut.bits.pc        := io.msgIn.bits.pc
  io.msgOut.bits.snpc      := io.msgIn.bits.snpc
  io.msgOut.bits.pdnpc     := io.msgIn.bits.pdnpc
  io.msgOut.bits.wbEn      := io.msgIn.bits.wbEn
  io.msgOut.bits.wbSel     := io.msgIn.bits.wbSel
  io.msgOut.bits.rdIdx     := io.msgIn.bits.rdIdx
  io.msgOut.bits.csrAddr   := io.msgIn.bits.csrAddr
  io.msgOut.bits.csrWbEn   := io.msgIn.bits.csrWbEn
  io.msgOut.bits.excpAdj   := io.msgIn.bits.excpAdj
  io.msgOut.bits.pcSel     := io.msgIn.bits.pcSel
  io.msgOut.bits.imm       := io.msgIn.bits.imm
  io.msgOut.bits.break     := io.msgIn.bits.break

  private object State extends CvtChiselEnum {
    val S_Idle   = Value
    val S_GprReq = Value
    val S_Gpr    = Value
    val S_CsrReq = Value
    val S_Csr    = Value
    val S_Done   = Value
  }
  import State._
  private val (firstAction, _) = State.safe(
    decoder(
      Cat(bad, gprNop, csrNop),
      TruthTable(
        Seq(
          "b1??".BP -> S_Done.BP,
          "b011".BP -> S_Done.BP,
          "b00?".BP -> S_GprReq.BP,
          "b010".BP -> S_CsrReq.BP
        ),
        S_Idle.BP
      )
    )
  )
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle   -> Mux(io.msgIn.valid, firstAction, S_Idle),
      S_GprReq -> S_Gpr,
      S_Gpr    -> Mux(csrNop, S_Done, S_CsrReq),
      S_CsrReq -> S_Csr,
      S_Csr    -> S_Done,
      S_Done   -> Mux(io.msgOut.ready, S_Idle, S_Done)
    )
  )

  // SRAM response is returned at the next cycle
  gprRespValid := y === S_Gpr
  csrRespValid := y === S_Csr

  io.gprRead.valid := y === S_GprReq
  io.csrRead.valid := y === S_CsrReq

  io.msgIn.ready  := y === S_Idle & ~io.msgIn.valid
  io.msgOut.valid := y === S_Done
}
