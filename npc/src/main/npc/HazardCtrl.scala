package npc

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import common._
import npc._

class HazardCtrl extends Module {
  class PipelineCtrl extends common.PipelineCtrl {
    val stall = Bool()
    val flush = Bool()
  }

  class Port extends Bundle {
    val iduOutMsgValid = Input(Bool())
    val iduOutMsg      = Flipped(new Idu2ExuMsg)
    val exuInMsgValid  = Input(Bool())
    val exuInMsgReady  = Input(Bool())
    val exuInMsg       = Flipped(new Idu2ExuMsg)
    val exuOutMsgValid = Input(Bool())
    val exuOutMsg      = Flipped(new Exu2LsuMsg)
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
    (~io.exuInMsgReady & conflict(rs, io.exuInMsg.rdIdx) & io.exuInMsg.wbEn)
      | (~io.lsuInMsgReady & conflict(rs, io.lsuInMsg.rdIdx) & io.lsuInMsg.wbEn)
      | (~io.wbuInMsgReady & conflict(rs, io.wbuInMsg.rdIdx) & io.wbuInMsg.wbEn)
  )

  private val hasRwHazard = (
    io.iduOutMsgValid
      & (rwConflict(io.iduOutMsg.rs1Idx) | rwConflict(io.iduOutMsg.rs2Idx))
  )

  io.ifuCtrl.flush := false.B
  io.ifuCtrl.stall := false.B

  io.iduCtrl.flush := false.B
  io.iduCtrl.stall := false.B

  io.exuCtrl.flush := false.B
  io.exuCtrl.stall := hasRwHazard

  io.lsuCtrl.flush := false.B
  io.lsuCtrl.stall := false.B

  io.wbuCtrl.flush := false.B
  io.wbuCtrl.stall := false.B
}
