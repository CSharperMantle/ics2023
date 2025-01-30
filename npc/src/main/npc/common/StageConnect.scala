package npc.common

import chisel3._
import chisel3.util._

import npc.npc._

object StageConnect {
  def apply[TIn <: Data, TOut <: Data](
    prevOut: ReadyValidIO[TIn],
    selfIn:  ReadyValidIO[TIn],
    selfOut: ReadyValidIO[TOut]
  ) = {
    import ArchType._
    Arch match {
      case SingleCycle => prevOut.bits := selfIn.bits
      case MultiCycle  => prevOut      <> selfIn
      case Pipelined => {
        prevOut.ready := selfIn.ready
        selfIn.bits   := RegEnable(prevOut.bits, prevOut.fire)
        selfIn.valid  := RegEnable(prevOut.valid, false.B, prevOut.fire)
      }
      case OutOfOrder => ???
    }
  }
}
