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

class Lsu extends Module {
  require(XLen == 32, "Lsu for RV64 is not implemented")

  class Port extends Bundle {
    val msgIn  = Flipped(Irrevocable(new Exu2LsuMsg))
    val msgOut = Irrevocable(new Lsu2WbuMsg)
    val rReq   = Irrevocable(new MemReadReq(XLen.W))
    val rResp  = Flipped(Irrevocable(new MemReadResp(XLen.W)))
    val wReq   = Irrevocable(new MemWriteReq(XLen.W, 32.W))
    val wResp  = Flipped(Irrevocable(new MemWriteResp))
  }
  val io = IO(new Port)

  private val addr  = io.msgIn.bits.d
  private val wData = io.msgIn.bits.rs2

  private val memMask = MuxLookup(io.msgIn.bits.memWidth, 0.U)(
    Seq(
      MemWidth.LenB -> "b00000001".U(8.W),
      MemWidth.LenH -> "b00000011".U(8.W),
      MemWidth.LenW -> "b00001111".U(8.W)
    )
  )
  private val memSize = MuxLookup(io.msgIn.bits.memWidth, AxSize.Bytes1.U)(
    Seq(
      MemWidth.LenB -> AxSize.Bytes1.U,
      MemWidth.LenH -> AxSize.Bytes2.U,
      MemWidth.LenW -> AxSize.Bytes4.U
    )
  )

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
  private val alignBad = decoder(
    Cat(addr(1, 0), io.msgIn.bits.memWidth.U),
    TruthTable(
      Seq(
        "b00".BP ## MemWidth.LenB.BP,
        "b00".BP ## MemWidth.LenH.BP,
        "b00".BP ## MemWidth.LenW.BP,
        "b01".BP ## MemWidth.LenB.BP,
        "b10".BP ## MemWidth.LenB.BP,
        "b10".BP ## MemWidth.LenH.BP,
        "b11".BP ## MemWidth.LenB.BP
      ).map((bp) => bp -> 1.W.N),
      1.W.Y
    )
  ) === 1.W.Y

  private val wEn = io.msgIn.valid & io.msgIn.bits.memAction === MemAction.MemWt
  private val rEn =
    io.msgIn.valid & io.msgIn.bits.memAction.isOneOf(MemAction.MemRd, MemAction.MemRdu)

  io.rReq.bits.addr := addr
  io.rReq.bits.size := memSize
  private val rData = RegEnable(io.rResp.bits.data, io.rResp.valid)

  private val rDataShifted = MuxLookup(addr(1, 0), 0.U)(
    Seq(
      "b00".U -> rData,
      "b01".U -> Cat(Fill(8, 0.B), rData(XLen - 1, 8)),
      "b10".U -> Cat(Fill(16, 0.B), rData(XLen - 1, 16)),
      "b11".U -> Cat(Fill(24, 0.B), rData(XLen - 1, 24))
    )
  )

  private val sext = Module(new SExtender)
  sext.io.sextData := rDataShifted
  sext.io.sextW    := io.msgIn.bits.memWidth
  sext.io.sextU    := io.msgIn.bits.memAction === MemAction.MemRdu

  private val wDataShifted = MuxLookup(addr(1, 0), 0.U)(
    Seq(
      "b00".U -> wData,
      "b01".U -> Cat(wData(XLen - 9, 0), Fill(8, 0.B)),
      "b10".U -> Cat(wData(XLen - 17, 0), Fill(16, 0.B)),
      "b11".U -> Cat(wData(XLen - 25, 0), Fill(24, 0.B))
    )
  )
  io.wReq.bits.wData := wDataShifted
  io.wReq.bits.wAddr := addr
  io.wReq.bits.wSize := memSize

  private val wMaskShifted = MuxLookup(addr(1, 0), 0.U)(
    Seq(
      "b00".U -> memMask,
      "b01".U -> Cat(memMask(6, 0), Fill(1, 0.B)),
      "b10".U -> Cat(memMask(5, 0), Fill(2, 0.B)),
      "b11".U -> Cat(memMask(4, 0), Fill(3, 0.B))
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
    val S_WriteReq  = Value
    val S_Write     = Value
    val S_Wait4Next = Value
  }
  import State._
  private val (firstAction, _) = State.safe(
    decoder(
      Cat(io.msgIn.bits.bad, rEn, wEn, alignBad),
      TruthTable(
        Seq(
          "b0000".BP -> S_Wait4Next.BP,
          "b1???".BP -> S_Wait4Next.BP,
          "b0??1".BP -> S_Wait4Next.BP,
          "b01?0".BP -> S_ReadReq.BP,
          "b0010".BP -> S_WriteReq.BP
        ),
        S_Idle.BP
      )
    )
  )
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle      -> Mux(io.msgIn.valid, firstAction, S_Idle),
      S_ReadReq   -> Mux(io.rReq.ready, S_Read, S_ReadReq),
      S_Read      -> Mux(io.rResp.valid, Mux(wEn, S_WriteReq, S_Wait4Next), S_Read),
      S_WriteReq  -> Mux(io.wReq.ready, S_Write, S_WriteReq),
      S_Write     -> Mux(io.wResp.valid, S_Wait4Next, S_Write),
      S_Wait4Next -> Mux(io.msgOut.ready, S_Idle, S_Wait4Next)
    )
  )

  private val memRResp = RegEnable(io.rResp.bits.rResp, RResp.Okay, io.rResp.valid)
  private val memWResp = RegEnable(io.wResp.bits.bResp, BResp.Okay, io.wResp.valid)

  io.rReq.valid  := y === S_ReadReq
  io.rResp.ready := io.rResp.valid & y === S_Wait4Next

  io.wReq.valid  := y === S_WriteReq
  io.wResp.ready := io.wResp.valid & y === S_Wait4Next

  io.msgOut.bits.pc       := io.msgIn.bits.pc
  io.msgOut.bits.wbSel    := io.msgIn.bits.wbSel
  io.msgOut.bits.d        := io.msgIn.bits.d
  io.msgOut.bits.memRData := sext.io.sextRes
  io.msgOut.bits.csrVal   := io.msgIn.bits.csrVal
  io.msgOut.bits.rdIdx    := io.msgIn.bits.rdIdx
  io.msgOut.bits.wbEn     := io.msgIn.bits.wbEn
  io.msgOut.bits.bad := io.msgIn.bits.bad |
    ((rEn | wEn) & alignBad) |
    (rEn & ~(memRResp === RResp.Okay)) |
    (wEn & ~(memWResp === BResp.Okay))

  io.msgOut.bits.pcSel   := io.msgIn.bits.pcSel
  io.msgOut.bits.brTaken := io.msgIn.bits.brTaken
  io.msgOut.bits.imm     := io.msgIn.bits.imm
  io.msgOut.bits.mepc    := io.msgIn.bits.mepc
  io.msgOut.bits.mtvec   := io.msgIn.bits.mtvec

  io.msgIn.ready  := y === S_Wait4Next & io.msgOut.ready
  io.msgOut.valid := y === S_Wait4Next
}
