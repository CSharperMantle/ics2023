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
  val msgIn  = Flipped(Irrevocable(new Exu2LsuMsg))
  val msgOut = Irrevocable(new Lsu2WbuMsg)
  val rReq   = Irrevocable(new MemReadReq(XLen.W))
  val rResp  = Flipped(Irrevocable(new MemReadResp(XLen.W)))
  val wReq   = Irrevocable(new MemWriteReq(XLen.W, 32.W))
  val wResp  = Flipped(Irrevocable(new MemWriteResp))
}

class Lsu extends Module {
  require(XLen == 32, "Lsu for RV64 is not implemented")

  val io = IO(new LsuIO)

  private val memRAddr = io.msgIn.bits.d
  private val memWAddr = io.msgIn.bits.d
  private val memWData = io.msgIn.bits.rs2

  private val memActionDec = Decoder1H(
    Seq(
      MemAction.MemRd.BP   -> 0,
      MemAction.MemRdu.BP  -> 1,
      MemAction.MemWt.BP   -> 2,
      MemAction.MemNone.BP -> 3
    )
  )
  private val memAction1H = memActionDec(io.msgIn.bits.memAction)

  private val memWidthDec = Decoder1H(
    Seq(
      MemWidth.LenB.BP -> 0,
      MemWidth.LenH.BP -> 1,
      MemWidth.LenW.BP -> 2
    )
  )
  private val memWidth1H = memWidthDec(io.msgIn.bits.memWidth)
  private val memMask = Mux1H(
    Seq(
      memWidth1H(0) -> "b00000001".U(8.W),
      memWidth1H(1) -> "b00000011".U(8.W),
      memWidth1H(2) -> "b00001111".U(8.W),
      memWidth1H(3) -> 0.U(8.W)
    )
  )

  private val memAlignDec = Decoder1H(
    Seq(
      "b00".BP -> 0,
      "b01".BP -> 1,
      "b10".BP -> 2,
      "b11".BP -> 3
    )
  )

  private val wEn = io.msgIn.valid & memAction1H(2)
  private val rEn = io.msgIn.valid & (memAction1H(0) | memAction1H(1))

  io.rReq.bits.addr := Cat(memRAddr(XLen - 1, 2), Fill(2, 0.B))
  private val readData = RegEnable(io.rResp.bits.data, io.rResp.valid)

  private val memRAlign1H = memAlignDec(memRAddr(1, 0))
  private val memRDataShifted = Mux1H(
    Seq(
      memRAlign1H(0) -> readData,
      memRAlign1H(1) -> Cat(Fill(8, 0.B), readData(XLen - 1, 8)),
      memRAlign1H(2) -> Cat(Fill(16, 0.B), readData(XLen - 1, 16)),
      memRAlign1H(3) -> Cat(Fill(24, 0.B), readData(XLen - 1, 24)),
      memRAlign1H(4) -> 0.U
    )
  )

  private val sext = Module(new SExtender)
  sext.io.sextData := memRDataShifted
  sext.io.sextW    := io.msgIn.bits.memWidth
  sext.io.sextU    := memAction1H(1)

  private val memWAlign1H = memAlignDec(memWAddr(1, 0))
  private val memWDataShifted = Mux1H(
    Seq(
      memWAlign1H(0) -> memWData,
      memWAlign1H(1) -> Cat(memWData(XLen - 9, 0), Fill(8, 0.B)),
      memWAlign1H(2) -> Cat(memWData(XLen - 17, 0), Fill(16, 0.B)),
      memWAlign1H(3) -> Cat(memWData(XLen - 25, 0), Fill(24, 0.B)),
      memWAlign1H(4) -> 0.U
    )
  )
  io.wReq.bits.wData := memWDataShifted
  io.wReq.bits.wAddr := Cat(memWAddr(XLen - 1, 2), Fill(2, 0.B))
  private val memWMaskShifted = Mux1H(
    Seq(
      memWAlign1H(0) -> memMask,
      memWAlign1H(1) -> Cat(memMask(6, 0), Fill(1, 0.B)),
      memWAlign1H(2) -> Cat(memMask(5, 0), Fill(2, 0.B)),
      memWAlign1H(3) -> Cat(memMask(4, 0), Fill(3, 0.B)),
      memWAlign1H(4) -> 0.U
    )
  )

  io.wReq.bits.wMask := MuxCase(
    0.U,
    Seq(
      rEn -> memMask,
      wEn -> memWMaskShifted
    )
  )

  private object State extends CvtChiselEnum {
    val S_Idle      = Value
    val S_ReadReq   = Value
    val S_Read      = Value
    val S_ReadDone  = Value
    val S_WriteReq  = Value
    val S_Write     = Value
    val S_WriteDone = Value
    val S_WaitReady = Value
  }
  import State._
  private val firstAction =
    MuxCase(
      S_Idle,
      Seq(
        (~rEn & ~wEn) -> S_WaitReady,
        (rEn & ~wEn)  -> S_ReadReq,
        (~rEn & wEn)  -> S_WriteReq,
        (rEn & wEn)   -> S_ReadReq
      )
    )
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle      -> Mux(io.msgIn.valid, firstAction, S_Idle),
      S_ReadReq   -> Mux(io.rReq.ready, S_Read, S_ReadReq),
      S_Read      -> Mux(io.rResp.valid, S_ReadDone, S_Read),
      S_ReadDone  -> Mux(wEn, S_Write, S_WaitReady),
      S_WriteReq  -> Mux(io.wReq.ready, S_WriteDone, S_WriteReq),
      S_Write     -> Mux(io.wResp.valid, S_WriteDone, S_Write),
      S_WriteDone -> S_WaitReady,
      S_WaitReady -> Mux(io.msgOut.ready, S_Idle, S_WaitReady)
    )
  )

  private val memRResp = RegEnable(io.rResp.bits.resp, RResp.Okay, io.rResp.valid)
  private val memWResp = RegEnable(io.wResp.bits.bResp, BResp.Okay, io.wResp.valid)

  io.rReq.valid  := y === S_ReadReq
  io.rResp.ready := y === S_ReadDone

  io.wReq.valid  := y === S_WriteReq
  io.wResp.ready := y === S_WriteDone

  io.msgOut.bits.pc       := io.msgIn.bits.pc
  io.msgOut.bits.wbSel    := io.msgIn.bits.wbSel
  io.msgOut.bits.d        := io.msgIn.bits.d
  io.msgOut.bits.memRData := sext.io.sextRes
  io.msgOut.bits.csrVal   := io.msgIn.bits.csrVal
  io.msgOut.bits.rdIdx    := io.msgIn.bits.rdIdx
  io.msgOut.bits.wbEn     := io.msgIn.bits.wbEn
  io.msgOut.bits.inval := io.msgIn.bits.inval |
    memAction1H(memActionDec.bitBad) |
    (rEn & memRAlign1H(memAlignDec.bitBad)) |
    (rEn & ~(memRResp === RResp.Okay)) |
    (wEn & memWAlign1H(memAlignDec.bitBad)) |
    (wEn & ~(memWResp === BResp.Okay))

  io.msgOut.bits.pcSel   := io.msgIn.bits.pcSel
  io.msgOut.bits.brTaken := io.msgIn.bits.brTaken
  io.msgOut.bits.imm     := io.msgIn.bits.imm
  io.msgOut.bits.mepc    := io.msgIn.bits.mepc
  io.msgOut.bits.mtvec   := io.msgIn.bits.mtvec

  io.msgIn.ready  := y === S_WaitReady
  io.msgOut.valid := y === S_WaitReady
}
