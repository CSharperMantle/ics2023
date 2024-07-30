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
  val MemRd   = Value
  val MemRdu  = Value
  val MemWt   = Value
  val MemNone = Value
}

class Lsu2WbuMsg extends Bundle {
  val pc       = Output(UInt(XLen.W))
  val wbSel    = Output(WbSelField.chiselType)
  val d        = Output(UInt(XLen.W))
  val memRData = Output(UInt(XLen.W))
  val csrVal   = Output(UInt(XLen.W))
  val rdIdx    = Output(UInt(5.W))
  val wbEn     = Output(WbEnField.chiselType)
  val inval    = Output(Bool())
  // Pass-through for Wbu
  val pcSel   = Output(PcSelField.chiselType)
  val brTaken = Output(Bool())
  val imm     = Output(UInt(XLen.W))
  val mepc    = Output(UInt(XLen.W))
  val mtvec   = Output(UInt(XLen.W))
}

class LsuIO extends Bundle {
  val msgIn  = Flipped(Decoupled(new Exu2LsuMsg))
  val msgOut = Decoupled(new Lsu2WbuMsg)
}

class Lsu extends Module {
  require(XLen == 32, "Lsu for RV64 is not implemented")

  val io = IO(new LsuIO)

  val memRAddr = io.msgIn.bits.d
  val memWAddr = io.msgIn.bits.d
  val memWData = io.msgIn.bits.rs2

  val memActionDec = Decoder1H(
    Seq(
      MemAction.MemRd.BP   -> 0,
      MemAction.MemRdu.BP  -> 1,
      MemAction.MemWt.BP   -> 2,
      MemAction.MemNone.BP -> 3
    )
  )
  val memAction1H = memActionDec(io.msgIn.bits.memAction)

  val memWidthDec = Decoder1H(
    Seq(
      MemWidth.LenB.BP -> 0,
      MemWidth.LenH.BP -> 1,
      MemWidth.LenW.BP -> 2
    )
  )
  val memWidth1H = memWidthDec(io.msgIn.bits.memWidth)
  val memMask = Mux1H(
    Seq(
      memWidth1H(0) -> "b00000001".U(8.W),
      memWidth1H(1) -> "b00000011".U(8.W),
      memWidth1H(2) -> "b00001111".U(8.W),
      memWidth1H(3) -> 0.U(8.W)
    )
  )

  val memAlignDec = Decoder1H(
    Seq(
      "b00".BP -> 0,
      "b01".BP -> 1,
      "b10".BP -> 2,
      "b11".BP -> 3
    )
  )

  val wEn = io.msgIn.valid & memAction1H(2)
  val rEn = io.msgIn.valid & (memAction1H(0) | memAction1H(1))

  val writePort = Module(new SramWPort(XLen.W, XLen.W))
  val readPort  = Module(new SramRPort(XLen.W, XLen.W))
  readPort.io.rAddr := Cat(memRAddr(XLen - 1, 2), Fill(2, 0.B))

  val memWAlign1H = memAlignDec(memWAddr(1, 0))
  val memWDataShifted = Mux1H(
    Seq(
      memWAlign1H(0) -> memWData,
      memWAlign1H(1) -> Cat(memWData(XLen - 9, 0), Fill(8, 0.B)),
      memWAlign1H(2) -> Cat(memWData(XLen - 17, 0), Fill(16, 0.B)),
      memWAlign1H(3) -> Cat(memWData(XLen - 25, 0), Fill(24, 0.B)),
      memWAlign1H(4) -> 0.U
    )
  )
  writePort.io.wData := memWDataShifted
  writePort.io.wAddr := Cat(memWAddr(XLen - 1, 2), Fill(2, 0.B))
  val memWMaskShifted = Mux1H(
    Seq(
      memWAlign1H(0) -> memMask,
      memWAlign1H(1) -> Cat(memMask(6, 0), Fill(1, 0.B)),
      memWAlign1H(2) -> Cat(memMask(5, 0), Fill(2, 0.B)),
      memWAlign1H(3) -> Cat(memMask(4, 0), Fill(3, 0.B)),
      memWAlign1H(4) -> 0.U
    )
  )

  writePort.io.wMask := MuxCase(
    0.U,
    Seq(
      rEn -> memMask,
      wEn -> memWMaskShifted
    )
  )

  val memRAlign1H = memAlignDec(memRAddr(1, 0))
  val memRDataShifted = Mux1H(
    Seq(
      memRAlign1H(0) -> readPort.io.rData,
      memRAlign1H(1) -> Cat(Fill(8, 0.B), readPort.io.rData(XLen - 1, 8)),
      memRAlign1H(2) -> Cat(Fill(16, 0.B), readPort.io.rData(XLen - 1, 16)),
      memRAlign1H(3) -> Cat(Fill(24, 0.B), readPort.io.rData(XLen - 1, 24)),
      memRAlign1H(4) -> 0.U
    )
  )

  val sext = Module(new SExtender)
  sext.io.sextData := memRDataShifted
  sext.io.sextW    := io.msgIn.bits.memWidth
  sext.io.sextU    := memAction1H(1)

  object State extends CvtChiselEnum {
    val S_Idle      = Value
    val S_Read      = Value
    val S_Write     = Value
    val S_WaitReady = Value
  }
  import State._
  val firstAction =
    MuxCase(
      S_Idle,
      Seq(
        (~rEn & ~wEn) -> S_WaitReady,
        (rEn & ~wEn)  -> S_Read,
        (~rEn & wEn)  -> S_Write,
        (rEn & wEn)   -> S_Read
      )
    )
  val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle      -> Mux(io.msgIn.valid, firstAction, S_Idle),
      S_Read      -> Mux(readPort.io.done, Mux(wEn, S_Write, S_WaitReady), S_Read),
      S_Write     -> Mux(writePort.io.done, S_WaitReady, S_Write),
      S_WaitReady -> Mux(io.msgOut.ready, S_Idle, S_WaitReady)
    )
  )

  readPort.io.rEn  := y === S_Read
  writePort.io.wEn := y === S_Write

  io.msgOut.bits.pc       := io.msgIn.bits.pc
  io.msgOut.bits.wbSel    := io.msgIn.bits.wbSel
  io.msgOut.bits.d        := io.msgIn.bits.d
  io.msgOut.bits.memRData := sext.io.sextRes
  io.msgOut.bits.csrVal   := io.msgIn.bits.csrVal
  io.msgOut.bits.rdIdx    := io.msgIn.bits.rdIdx
  io.msgOut.bits.wbEn     := io.msgIn.bits.wbEn
  io.msgOut.bits.inval := io.msgIn.bits.inval |
    memAction1H(memActionDec.bitBad) |
    memWAlign1H(memAlignDec.bitBad) |
    memRAlign1H(memAlignDec.bitBad)

  io.msgOut.bits.pcSel   := io.msgIn.bits.pcSel
  io.msgOut.bits.brTaken := io.msgIn.bits.brTaken
  io.msgOut.bits.imm     := io.msgIn.bits.imm
  io.msgOut.bits.mepc    := io.msgIn.bits.mepc
  io.msgOut.bits.mtvec   := io.msgIn.bits.mtvec

  io.msgIn.ready  := y === S_WaitReady
  io.msgOut.valid := y === S_WaitReady
}
