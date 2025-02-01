package npc

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import common._
import npc._

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

  private val mispredicted = io.exuOutMsgValid & io.dnpc =/= io.exuOutMsg.pdnpc

  private val flushIcache = io.iduOutMsgValid & io.iduOutMsg.icacheFlush

  io.ifuCtrl.flush := mispredicted | flushIcache
  io.ifuCtrl.stall := false.B

  io.iduCtrl.flush := mispredicted
  io.iduCtrl.stall := false.B

  io.exuCtrl.flush := false.B
  io.exuCtrl.stall := hasRwHazard

  io.lsuCtrl.flush := false.B
  io.lsuCtrl.stall := false.B

  io.wbuCtrl.flush := false.B
  io.wbuCtrl.stall := false.B
}
