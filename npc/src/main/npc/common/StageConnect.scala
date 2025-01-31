package npc.common

import chisel3._
import chisel3.util._

import npc.npc._

trait PipelineCtrl extends Bundle {
  val stall: Bool
  val flush: Bool
}

object StageConnect {
  def apply[TIn <: Data, TOut <: Data](
    prevOut: ReadyValidIO[TIn],
    selfIn:  ReadyValidIO[TIn],
    ctrl:    PipelineCtrl) = {
    import ArchType._
    Arch match {
      case SingleCycle => prevOut.bits := selfIn.bits
      case MultiCycle  => prevOut      <> selfIn
      case Pipelined => {
        prevOut.ready := selfIn.ready & ~ctrl.stall
        selfIn.bits   := RegEnable(prevOut.bits, prevOut.fire & ~ctrl.stall)
        selfIn.valid  := RegEnable(prevOut.valid & ~ctrl.flush & ~ctrl.stall, false.B, selfIn.ready)
      }
      case OutOfOrder => ???
    }
  }
}
