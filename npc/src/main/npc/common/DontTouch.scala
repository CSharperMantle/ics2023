package npc.common

import chisel3._
import chisel3.util._

object MuxDontTouch {
  def apply[T <: Data](cond: Bool, con: T, alt: T): T = {
    Mux(cond, dontTouch(WireInit(con)), dontTouch(WireInit(alt)))
  }
}

object MuxCaseDontTouch {
  def apply[T <: Data](default: T, mapping: Seq[(Bool, T)]): T = {
    MuxCase(
      dontTouch(WireInit(default)),
      mapping.map((pair) => pair._1 -> dontTouch(WireInit(pair._2))))
  }
}
