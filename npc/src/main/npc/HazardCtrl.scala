package npc

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import common._
import npc._

class FwdEn extends Bundle {
  val rs1 = Bool()
  val rs2 = Bool()
  val csr = Bool()
}

class TrapFwdEn extends Bundle {
  val mepc  = Bool()
  val mtvec = Bool()
}

class TrapFwdVal extends Bundle {
  val mepc  = UInt(XLen.W)
  val mtvec = UInt(XLen.W)
}

class HazardCtrl extends Module {
  class PipelineCtrl extends common.PipelineCtrl {
    override val stall = Bool()
    override val flush = Bool()
  }

  class Port extends Bundle {
    val ifuOutMsgValid = Input(Bool())
    val ifuOutMsg      = Flipped(new Ifu2IduMsg)
    val iduInMsgValid  = Input(Bool())
    val iduInMsg       = Flipped(new Ifu2IduMsg)
    val iduOutMsgValid = Input(Bool())
    val iduOutMsg      = Flipped(new Idu2ExuMsg)
    val exuInMsgValid  = Input(Bool())
    val exuInMsg       = Flipped(new Idu2ExuMsg)
    val exuOutMsgValid = Input(Bool())
    val exuOutMsg      = Flipped(new Exu2LsuMsg)
    val dnpc           = Input(UInt(XLen.W))
    val lsuInMsgValid  = Input(Bool())
    val lsuInMsgReady  = Input(Bool())
    val lsuInMsg       = Flipped(new Exu2LsuMsg)
    val lsuOutMsgValid = Input(Bool())
    val lsuOutMsg      = Flipped(new Lsu2WbuMsg)
    val wbuInMsgValid  = Input(Bool())
    val wbuInMsgReady  = Input(Bool())
    val wbuInMsg       = Flipped(new Lsu2WbuMsg)
    val ifuCtrl        = Output(new PipelineCtrl)
    val iduCtrl        = Output(new PipelineCtrl)
    val exuCtrl        = Output(new PipelineCtrl)
    val lsuCtrl        = Output(new PipelineCtrl)
    val wbuCtrl        = Output(new PipelineCtrl)
    val fwdEn          = Output(new FwdEn)
    val fwdGprVal      = Output(UInt(XLen.W))
    val fwdCsrVal      = Output(UInt(XLen.W))
    val trapped        = Output(Bool())
    val trapFwdEn      = Output(new TrapFwdEn)
    val trapFwdVal     = Output(new TrapFwdVal)
  }
  val io = IO(new Port)

  private def gprConflict(rs: UInt, rd: UInt) = rs === rd & rd =/= 0.U

  private def gprRwConflict(rs: UInt) = (
    (io.exuInMsgValid & gprConflict(rs, io.exuInMsg.rdIdx) & io.exuInMsg.wbEn)
      | (io.lsuInMsgValid & gprConflict(rs, io.lsuInMsg.rdIdx) & io.lsuInMsg.wbEn)
      | (io.wbuInMsgValid & gprConflict(rs, io.wbuInMsg.rdIdx) & io.wbuInMsg.wbEn)
  )

  private val hasGprRwHazard = (
    io.iduOutMsgValid
      & (gprRwConflict(io.iduOutMsg.rs1Idx) | gprRwConflict(io.iduOutMsg.rs2Idx))
  )

  private def csrConflict(srcIdx: UInt, dstIdx: UInt) = srcIdx === dstIdx

  private def csrRwConflict(srcIdx: UInt) = (
    (io.exuInMsgValid & csrConflict(srcIdx, io.exuInMsg.csrAddr) & io.exuInMsg.csrWbEn)
      | (io.lsuInMsgValid & csrConflict(srcIdx, io.lsuInMsg.csrAddr) & io.lsuInMsg.csrWbEn)
      | (io.wbuInMsgValid & csrConflict(srcIdx, io.wbuInMsg.csrAddr) & io.wbuInMsg.csrWbEn)
  )

  private val hasCsrRwHazard = io.iduOutMsgValid & csrRwConflict(io.iduOutMsg.csrAddr)

  // ALU ops/csrrx result ready at end of EXU
  private val exuResultGprFwdable = (
    io.exuOutMsgValid
      & io.exuOutMsg.wbEn
      & io.exuOutMsg.wbSel.isOneOf(WbSel.WbAlu, WbSel.WbCsr)
  )
  // EXU handshake has completed, so go for LSU register
  private val lsuInputGprFwdable = (
    io.lsuInMsgValid
      & io.lsuInMsg.wbEn
      & io.lsuInMsg.wbSel.isOneOf(WbSel.WbAlu, WbSel.WbCsr)
  )
  // lx result ready at end of LSU
  private val lsuResultGprFwdable = (
    io.lsuOutMsgValid
      & io.lsuOutMsg.wbEn
      & io.lsuOutMsg.wbSel === WbSel.WbMem
  )
  private val anyGprFwdable = exuResultGprFwdable | lsuInputGprFwdable | lsuResultGprFwdable

  private val fwdGprIdx = MuxCase(
    0.U,
    Seq(
      exuResultGprFwdable -> io.exuOutMsg.rdIdx,
      lsuInputGprFwdable  -> io.lsuInMsg.rdIdx,
      lsuResultGprFwdable -> io.lsuOutMsg.rdIdx
    )
  )
  private val fwdGprVal = MuxCase(
    0.U,
    Seq(
      exuResultGprFwdable -> Mux(
        io.exuOutMsg.wbSel === WbSel.WbAlu,
        io.exuOutMsg.d,
        io.exuOutMsg.csrVal
      ),
      lsuInputGprFwdable -> Mux(
        io.lsuInMsg.wbSel === WbSel.WbAlu,
        io.lsuInMsg.d,
        io.lsuInMsg.csrVal
      ),
      lsuResultGprFwdable -> io.lsuOutMsg.memRData
    )
  )

  private val exuResultCsrFwdable = io.exuOutMsgValid & io.exuOutMsg.csrWbEn
  private val lsuInputCsrFwdable  = io.lsuInMsgValid & io.lsuInMsg.csrWbEn
  private val lsuResultCsrFwdable = io.lsuOutMsgValid & io.lsuOutMsg.csrWbEn
  private val anyCsrFwdable       = exuResultCsrFwdable | lsuInputCsrFwdable | lsuResultCsrFwdable

  private val fwdCsrAddr = MuxCase(
    0.U,
    Seq(
      exuResultCsrFwdable -> io.exuOutMsg.csrAddr,
      lsuInputCsrFwdable  -> io.lsuInMsg.csrAddr,
      lsuResultCsrFwdable -> io.lsuOutMsg.csrAddr
    )
  )
  private val fwdCsrVal = MuxCase(
    0.U,
    Seq(
      exuResultCsrFwdable -> io.exuOutMsg.csrVal,
      lsuInputCsrFwdable  -> io.lsuInMsg.csrVal,
      lsuResultCsrFwdable -> io.lsuOutMsg.csrVal
    )
  )

  private val fwdable = Wire(new FwdEn)
  fwdable.rs1 := io.iduOutMsgValid & io.iduOutMsg.rs1Idx === fwdGprIdx & anyGprFwdable
  fwdable.rs2 := io.iduOutMsgValid & io.iduOutMsg.rs2Idx === fwdGprIdx & anyGprFwdable
  fwdable.csr := io.iduOutMsgValid & io.iduOutMsg.csrAddr === fwdCsrAddr & anyCsrFwdable

  private val mispredicted = io.exuOutMsgValid & io.dnpc =/= io.exuOutMsg.pdnpc

  private val flushIcache = io.iduOutMsgValid & io.iduOutMsg.icacheFlush

  private val ifuOutTrap = io.ifuOutMsgValid & Seq(
    io.ifuOutMsg.ifuExcp
  ).map(_.asUInt.orR).reduce(_ | _)
  private val iduInTrap = io.iduInMsgValid & Seq(
    io.iduInMsg.ifuExcp
  ).map(_.asUInt.orR).reduce(_ | _)
  private val iduOutTrap = io.iduOutMsgValid & Seq(
    io.iduOutMsg.ifuExcp,
    io.iduOutMsg.iduExcp
  ).map(_.asUInt.orR).reduce(_ | _)
  private val lsuInTrap = io.lsuInMsgValid & Seq(
    io.lsuInMsg.ifuExcp,
    io.lsuInMsg.iduExcp
  ).map(_.asUInt.orR).reduce(_ | _)
  private val lsuOutTrap = io.lsuOutMsgValid & Seq(
    io.lsuOutMsg.ifuExcp,
    io.lsuOutMsg.iduExcp,
    io.lsuOutMsg.lsuExcp
  ).map(_.asUInt.orR).reduce(_ | _)

  io.ifuCtrl.flush := mispredicted | flushIcache | iduInTrap | iduOutTrap | lsuInTrap | lsuOutTrap
  io.ifuCtrl.stall := false.B
  io.iduCtrl.flush := mispredicted | iduInTrap | iduOutTrap | lsuInTrap
  io.iduCtrl.stall := false.B
  io.exuCtrl.flush := lsuInTrap | lsuOutTrap
  io.exuCtrl.stall := (hasGprRwHazard | hasCsrRwHazard) & ~fwdable.asUInt.orR
  io.lsuCtrl.flush := lsuInTrap | lsuOutTrap
  io.lsuCtrl.stall := false.B
  io.wbuCtrl.flush := false.B
  io.wbuCtrl.stall := false.B

  io.fwdEn     := fwdable
  io.fwdGprVal := fwdGprVal
  io.fwdCsrVal := fwdCsrVal

  io.trapped := ifuOutTrap | iduInTrap | iduOutTrap | lsuInTrap | lsuOutTrap

  private def trapCsrFwdable(valid: Bool, en: Bool, dstAddr: UInt, csr: UInt) =
    valid & en & dstAddr === csr

  private def trapCsrExuOutFwdable(csr: UInt) =
    trapCsrFwdable(io.exuOutMsgValid, io.exuOutMsg.csrWbEn, io.exuOutMsg.csrAddr, csr)
  private def trapCsrLsuInFwdable(csr: UInt) =
    trapCsrFwdable(io.lsuInMsgValid, io.lsuInMsg.csrWbEn, io.lsuInMsg.csrAddr, csr)
  private def trapCsrLsuOutFwdable(csr: UInt) =
    trapCsrFwdable(io.lsuOutMsgValid, io.lsuOutMsg.csrWbEn, io.lsuOutMsg.csrAddr, csr)

  private val trapMepcExuOutFwdable = trapCsrExuOutFwdable("h341".U(12.W))
  private val trapMepcLsuInFwdable  = trapCsrLsuInFwdable("h341".U(12.W))
  private val trapMepcLsuOutFwdable = trapCsrLsuOutFwdable("h341".U(12.W))

  private val trapMtvecExuOutFwdable = trapCsrExuOutFwdable("h305".U(12.W))
  private val trapMtvecLsuInFwdable  = trapCsrLsuInFwdable("h305".U(12.W))
  private val trapMtvecLsuOutFwdable = trapCsrLsuOutFwdable("h305".U(12.W))

  private val trapFwdEn = Wire(new TrapFwdEn)
  trapFwdEn.mepc  := trapMepcExuOutFwdable | trapMepcLsuInFwdable | trapMepcLsuOutFwdable
  trapFwdEn.mtvec := trapMtvecExuOutFwdable | trapMtvecLsuInFwdable | trapMtvecLsuOutFwdable

  private val trapFwdVal = Wire(new TrapFwdVal)
  trapFwdVal.mepc := MuxCase(
    0.U,
    Seq(
      trapMepcExuOutFwdable -> io.exuOutMsg.csrVal,
      trapMepcLsuInFwdable  -> io.lsuInMsg.csrVal,
      trapMepcLsuOutFwdable -> io.lsuOutMsg.csrVal
    )
  )
  trapFwdVal.mtvec := MuxCase(
    0.U,
    Seq(
      trapMtvecExuOutFwdable -> io.exuOutMsg.csrVal,
      trapMtvecLsuInFwdable  -> io.lsuInMsg.csrVal,
      trapMtvecLsuOutFwdable -> io.lsuOutMsg.csrVal
    )
  )

  io.trapFwdEn  := trapFwdEn
  io.trapFwdVal := trapFwdVal
}
