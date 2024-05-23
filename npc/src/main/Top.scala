import chisel3._
import chisel3.util._

import npc._

class TopIO extends NpcIO {}

class Top extends Module {
  val io   = IO(new TopIO)
  val core = Module(new Npc)
  io <> core.io
}
