package npc

import chisel3._
import chisel3.util._

import common._
import npc._

object WbSel extends CvtChiselEnum {
  val WbAlu  = Value
  val WbSnpc = Value
  val WbMem  = Value
  val WbCsr  = Value
}

class WbuIO extends Bundle {
  val wbSel    = Input(UInt(WbSel.W))
  val dataAlu  = Input(UInt(XLen.W))
  val dataSnpc = Input(UInt(XLen.W))
  val dataMem  = Input(UInt(XLen.W))
  val dataCsr  = Input(UInt(XLen.W))
  val d        = Output(UInt(XLen.W))
  val inval    = Output(Bool())
}

class Wbu extends Module {
  import WbSel._

  val io = IO(new WbuIO)

  val wbSelDec = Decoder1H(
    Seq(
      WbSel.WbAlu.BP  -> 0,
      WbSel.WbSnpc.BP -> 1,
      WbSel.WbMem.BP  -> 2,
      WbSel.WbCsr.BP  -> 3
    )
  )
  val wbSel1H = wbSelDec(io.wbSel)
  val wbData = Mux1H(
    Seq(
      wbSel1H(0) -> io.dataAlu,
      wbSel1H(1) -> io.dataSnpc,
      wbSel1H(2) -> io.dataMem,
      wbSel1H(3) -> io.dataCsr,
      wbSel1H(4) -> 0.U
    )
  )

  io.d := wbData

  io.inval := wbSel1H(wbSelDec.bitBad)
}
