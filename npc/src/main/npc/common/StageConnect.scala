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
    selfOut: ReadyValidIO[TOut],
    ctrl:    PipelineCtrl) = {
    import ArchType._
    Arch match {
      case SingleCycle => prevOut.bits := selfIn.bits
      case MultiCycle  => prevOut      <> selfIn
      case Pipelined => {
        prevOut.ready := selfIn.ready & ~ctrl.stall
        selfIn.bits   := RegEnable(prevOut.bits, prevOut.fire)
        selfIn.valid := RegNext(
          MuxCase(
            selfIn.valid,
            Seq(
              ctrl.flush   -> false.B,
              prevOut.fire -> true.B,
              selfOut.fire -> false.B
            )
          ),
          false.B
        )
      }
      case OutOfOrder => ???
    }
  }
}
