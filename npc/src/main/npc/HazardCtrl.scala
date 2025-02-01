package npc

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import common._
import npc._

class FwdEn extends Bundle {
  val rs1 = Bool()
  val rs2 = Bool()
}

class HazardCtrl extends Module {
  class PipelineCtrl extends common.PipelineCtrl {
    override val stall = Bool()
    override val flush = Bool()
  }

  class Port extends Bundle {
    val iduOutMsgValid = Input(Bool())
    val iduOutMsg      = Flipped(new Idu2ExuMsg)
    val exuInMsgValid  = Input(Bool())
    val exuInMsgReady  = Input(Bool())
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
    val fwdRegVal      = Output(UInt(XLen.W))
  }
  val io = IO(new Port)

  private def conflict(rs: UInt, rd: UInt) = rs === rd & rd =/= 0.U

  private def rwConflict(rs: UInt) = (
    (io.exuInMsgValid & conflict(rs, io.exuInMsg.rdIdx) & io.exuInMsg.wbEn)
      | (io.lsuInMsgValid & conflict(rs, io.lsuInMsg.rdIdx) & io.lsuInMsg.wbEn)
      | (io.wbuInMsgValid & conflict(rs, io.wbuInMsg.rdIdx) & io.wbuInMsg.wbEn)
  )

  private val hasRwHazard = (
    io.iduOutMsgValid
      & (rwConflict(io.iduOutMsg.rs1Idx) | rwConflict(io.iduOutMsg.rs2Idx))
  )

  // ALU ops/csrrx result ready at end of EXU
  private val exuResultFwdable = (
    io.exuOutMsgValid
      & io.exuOutMsg.wbEn
      & io.exuOutMsg.wbSel.isOneOf(WbSel.WbAlu, WbSel.WbCsr)
  )
  // EXU handshake has completed, so go for LSU register
  private val lsuInputFwdable = (
    io.lsuInMsgValid
      & io.lsuInMsg.wbEn
      & io.lsuInMsg.wbSel.isOneOf(WbSel.WbAlu, WbSel.WbCsr)
  )
  // lx result ready at end of LSU
  private val lsuResultFwdable = (
    io.lsuOutMsgValid
      & io.lsuOutMsg.wbEn
      & io.lsuOutMsg.wbSel === WbSel.WbMem
  )
  private val anyFwdable = exuResultFwdable | lsuInputFwdable | lsuResultFwdable

  private val fwdRegIdx = MuxCase(
    0.U,
    Seq(
      exuResultFwdable -> io.exuOutMsg.rdIdx,
      lsuInputFwdable  -> io.lsuInMsg.rdIdx,
      lsuResultFwdable -> io.lsuOutMsg.rdIdx
    )
  )
  private val fwdRegVal = MuxCase(
    0.U,
    Seq(
      exuResultFwdable -> Mux(
        io.exuOutMsg.wbSel === WbSel.WbAlu,
        io.exuOutMsg.d,
        io.exuOutMsg.csrVal
      ),
      lsuInputFwdable -> Mux(
        io.lsuInMsg.wbSel === WbSel.WbAlu,
        io.lsuInMsg.d,
        io.lsuInMsg.csrVal
      ),
      lsuResultFwdable -> io.lsuOutMsg.memRData
    )
  )

  private val fwdable = Wire(new FwdEn)
  fwdable.rs1 := io.iduOutMsgValid & io.iduOutMsg.rs1Idx === fwdRegIdx & anyFwdable
  fwdable.rs2 := io.iduOutMsgValid & io.iduOutMsg.rs2Idx === fwdRegIdx & anyFwdable

  private val mispredicted = io.exuOutMsgValid & io.dnpc =/= io.exuOutMsg.pdnpc

  private val flushIcache = io.iduOutMsgValid & io.iduOutMsg.icacheFlush

  io.ifuCtrl.flush := mispredicted | flushIcache
  io.ifuCtrl.stall := false.B
  io.iduCtrl.flush := mispredicted
  io.iduCtrl.stall := false.B
  io.exuCtrl.flush := false.B
  io.exuCtrl.stall := hasRwHazard & ~(fwdable.rs1 | fwdable.rs2)
  io.lsuCtrl.flush := false.B
  io.lsuCtrl.stall := false.B
  io.wbuCtrl.flush := false.B
  io.wbuCtrl.stall := false.B

  io.fwdEn     := fwdable
  io.fwdRegVal := fwdRegVal
}
