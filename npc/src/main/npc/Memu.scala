package npc

import chisel3._
import chisel3.util._

import common._
import npc._

object MemWidth extends CvtChiselEnum {
  val LenB = Value
  val LenH = Value
  val LenW = Value
  val LenD = Value
}

object MemAction extends CvtChiselEnum {
  val Rd   = Value
  val Rdu  = Value
  val Wt   = Value
  val None = Value
}

class MemuIO extends Bundle {}

class Memu extends Module {
  val io = IO(new MemuIO)

  // TODO: Make it a blackbox.
}
