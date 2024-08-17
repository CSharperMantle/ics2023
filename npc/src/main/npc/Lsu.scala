package npc

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

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
  val bad      = Output(Bool())
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

  private val addr  = io.msgIn.bits.d
  private val wData = io.msgIn.bits.rs2

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
  private val memSize = Mux1H(
    Seq(
      memWidth1H(0) -> AxSize.Bytes1.U,
      memWidth1H(1) -> AxSize.Bytes2.U,
      memWidth1H(2) -> AxSize.Bytes4.U,
      memWidth1H(3) -> AxSize.Bytes1.U
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
  private val memAlign1H = memAlignDec(addr(1, 0))

  /*
   * Properly aligned access:
   * Byte: 0123
   *       +
   *       ++
   *       ++++
   *        +
   *         +
   *         ++
   *          +
   */
  private val alignBadTable = TruthTable(
    Seq(
      "b00".BP ## MemWidth.LenB.BP -> 1.N,
      "b00".BP ## MemWidth.LenH.BP -> 1.N,
      "b00".BP ## MemWidth.LenW.BP -> 1.N,
      "b01".BP ## MemWidth.LenB.BP -> 1.N,
      "b10".BP ## MemWidth.LenB.BP -> 1.N,
      "b10".BP ## MemWidth.LenH.BP -> 1.N,
      "b11".BP ## MemWidth.LenB.BP -> 1.N
    ),
    1.Y
  )
  private val alignBad = decoder(Cat(addr(1, 0), io.msgIn.bits.memWidth), alignBadTable) === 1.Y

  private val wEn = io.msgIn.valid & memAction1H(2)
  private val rEn = io.msgIn.valid & (memAction1H(0) | memAction1H(1))

  io.rReq.bits.addr := addr
  io.rReq.bits.size := memSize
  private val rData = RegEnable(io.rResp.bits.data, io.rResp.valid)

  private val rDataShifted = Mux1H(
    Seq(
      memAlign1H(0) -> rData,
      memAlign1H(1) -> Cat(Fill(8, 0.B), rData(XLen - 1, 8)),
      memAlign1H(2) -> Cat(Fill(16, 0.B), rData(XLen - 1, 16)),
      memAlign1H(3) -> Cat(Fill(24, 0.B), rData(XLen - 1, 24)),
      memAlign1H(4) -> 0.U
    )
  )

  private val sext = Module(new SExtender)
  sext.io.sextData := rDataShifted
  sext.io.sextW    := io.msgIn.bits.memWidth
  sext.io.sextU    := memAction1H(1)

  private val wDataShifted = Mux1H(
    Seq(
      memAlign1H(0) -> wData,
      memAlign1H(1) -> Cat(wData(XLen - 9, 0), Fill(8, 0.B)),
      memAlign1H(2) -> Cat(wData(XLen - 17, 0), Fill(16, 0.B)),
      memAlign1H(3) -> Cat(wData(XLen - 25, 0), Fill(24, 0.B)),
      memAlign1H(4) -> 0.U
    )
  )
  io.wReq.bits.wData := wDataShifted
  io.wReq.bits.wAddr := addr
  io.wReq.bits.wSize := memSize

  private val wMaskShifted = Mux1H(
    Seq(
      memAlign1H(0) -> memMask,
      memAlign1H(1) -> Cat(memMask(6, 0), Fill(1, 0.B)),
      memAlign1H(2) -> Cat(memMask(5, 0), Fill(2, 0.B)),
      memAlign1H(3) -> Cat(memMask(4, 0), Fill(3, 0.B)),
      memAlign1H(4) -> 0.U
    )
  )
  io.wReq.bits.wMask := MuxCase(
    0.U,
    Seq(
      rEn -> memMask,
      wEn -> wMaskShifted
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
    val S_Wait4Next = Value
  }
  import State._
  private val firstAction =
    MuxCase(
      S_Idle,
      Seq(
        (~rEn & ~wEn)            -> S_Wait4Next,
        ((rEn | wEn) & alignBad) -> S_Wait4Next,
        (rEn & ~wEn & ~alignBad) -> S_ReadReq,
        (~rEn & wEn & ~alignBad) -> S_WriteReq,
        (rEn & wEn & ~alignBad)  -> S_ReadReq
      )
    )
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle      -> Mux(io.msgIn.valid, firstAction, S_Idle),
      S_ReadReq   -> Mux(io.rReq.ready, S_Read, S_ReadReq),
      S_Read      -> Mux(io.rResp.valid, S_ReadDone, S_Read),
      S_ReadDone  -> Mux(wEn, S_WriteReq, S_Wait4Next),
      S_WriteReq  -> Mux(io.wReq.ready, S_Write, S_WriteReq),
      S_Write     -> Mux(io.wResp.valid, S_WriteDone, S_Write),
      S_WriteDone -> S_Wait4Next,
      S_Wait4Next -> Mux(io.msgOut.ready, S_Idle, S_Wait4Next)
    )
  )

  private val memRResp = RegEnable(io.rResp.bits.rResp, RResp.Okay.U, io.rResp.valid)
  private val memWResp = RegEnable(io.wResp.bits.bResp, BResp.Okay.U, io.wResp.valid)

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
  io.msgOut.bits.bad := io.msgIn.bits.bad |
    memAction1H(memActionDec.bitBad) |
    ((rEn | wEn) & alignBad) |
    ((rEn | wEn) & memAlign1H(memAlignDec.bitBad)) |
    (rEn & ~(memRResp === RResp.Okay.U)) |
    (wEn & ~(memWResp === BResp.Okay.U))

  io.msgOut.bits.pcSel   := io.msgIn.bits.pcSel
  io.msgOut.bits.brTaken := io.msgIn.bits.brTaken
  io.msgOut.bits.imm     := io.msgIn.bits.imm
  io.msgOut.bits.mepc    := io.msgIn.bits.mepc
  io.msgOut.bits.mtvec   := io.msgIn.bits.mtvec

  io.msgIn.ready  := y === S_Wait4Next
  io.msgOut.valid := y === S_Wait4Next
}
