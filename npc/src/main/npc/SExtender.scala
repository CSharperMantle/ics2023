package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class SExtender extends Module {
  class Port extends Bundle {
    val sextU    = Input(Bool())
    val sextW    = Input(MemWidth())
    val sextData = Input(UInt(XLen.W))
    val sextRes  = Output(UInt(XLen.W))
  }
  val io = IO(new Port)

  import MemWidth._

  private val sextResB = Cat(Fill(XLen - 8, Mux(io.sextU, 0.B, io.sextData(7))), io.sextData(7, 0))
  private val sextResH =
    Cat(Fill(XLen - 16, Mux(io.sextU, 0.B, io.sextData(15))), io.sextData(15, 0))
  private val sextResW =
    if (XLen == 32) io.sextData
    else Cat(Fill(XLen - 32, Mux(io.sextU, 0.B, io.sextData(31))), io.sextData(31, 0))
  private val sextResD = io.sextData

  io.sextRes := MuxLookup(io.sextW, 0.U)(
    Seq(
      LenB -> sextResB,
      LenH -> sextResH,
      LenW -> sextResW,
      LenD -> sextResD
    )
  )
}
