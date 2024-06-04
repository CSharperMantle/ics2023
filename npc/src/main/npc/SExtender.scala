package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class SExtenderIO extends Bundle {
  val sextU    = Input(Bool())
  val sextW    = Input(UInt(MemWidth.W))
  val sextData = Input(UInt(XLen.W))
  val sextRes  = Output(UInt(XLen.W))
}

class SExtender extends Module {
  import MemWidth._

  val io = IO(new SExtenderIO)

  val sextResB = Cat(Fill(XLen - 8, Mux(io.sextU, 0.B, io.sextData(7))), io.sextData(7, 0))
  val sextResH = Cat(Fill(XLen - 16, Mux(io.sextU, 0.B, io.sextData(15))), io.sextData(15, 0))
  val sextResW =
    if (XLen == 32) io.sextData
    else Cat(Fill(XLen - 32, Mux(io.sextU, 0.B, io.sextData(31))), io.sextData(31, 0))
  val sextResD = io.sextData
  val sextWDec = Decoder1H(
    Seq(
      LenB.BP -> 0,
      LenH.BP -> 1,
      LenW.BP -> 2,
      LenD.BP -> 3
    )
  )
  val sextW1H = sextWDec(io.sextW)
  io.sextRes := Mux1H(
    Seq(
      sextW1H(0) -> sextResB,
      sextW1H(1) -> sextResH,
      sextW1H(2) -> sextResW,
      sextW1H(3) -> sextResD,
      sextW1H(4) -> 0.U
    )
  )
}
