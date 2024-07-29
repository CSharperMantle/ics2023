package npc.common

import chisel3._
import chisel3.util._

import npc.npc._

object StageConnect {
  def apply[T <: Data](left: DecoupledIO[T], right: DecoupledIO[T]) = {
    import ArchType._
     Arch match {
      case SingleCycle => right.bits := left.bits
      case MultiCycle => right <> left
      case Pipelined => right <> RegEnable(left, left.fire)
      case OutOfOrder => right <> Queue(left, 16)
    }
  }
}
