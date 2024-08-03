import chisel3._
import chisel3.experimental._
import chisel3.util._

import npc._

class TopIO extends CoreIO {}

class Top extends Module {
  override def desiredName: String = "ysyx_23060288"

  val io = IO(new TopIO)

  private val core = Module(new Core)
  io <> core.io
}
