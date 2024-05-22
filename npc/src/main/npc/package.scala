package npc

import chisel3._

object npc {
  val XLen = 32
  val InitPCVal = "h80000000".U(XLen.W)
}
