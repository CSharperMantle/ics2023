package npc

import chisel3._

object npc {
  val XLen = 32
  require(XLen == 32 || XLen == 64)
  
  val InitPCVal = "h80000000".U(XLen.W)
}
