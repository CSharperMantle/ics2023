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

class LsuExcp extends Bundle {
  val loadMisalign  = Bool()
  val loadAccess    = Bool()
  val storeMisalign = Bool()
  val storeAccess   = Bool()
}

class Lsu2WbuMsg extends Bundle {
  // GEN
  // Used by Wbu
  val memRData = Output(UInt(XLen.W))
  val lsuExcp  = Output(new LsuExcp)
  // Unused by Wbu
  // (none)
  // PASS-THRU
  val instr   = Output(UInt(XLen.W))
  val d       = Output(UInt(XLen.W))
  val pc      = Output(UInt(XLen.W))
  val snpc    = Output(UInt(XLen.W))
  val ifuExcp = Output(new IfuExcp)
  val iduExcp = Output(new IduExcp)
  val wbEn    = Output(WbEnField.chiselType)
  val wbSel   = Output(WbSelField.chiselType)
  val pcSel   = Output(PcSelField.chiselType)
  val rdIdx   = Output(UInt(5.W))
  val csrVal  = Output(UInt(XLen.W))
  val csrAddr = Output(UInt(12.W))
  val csrWbEn = Output(CsrWbEnField.chiselType)
  val excpAdj = Output(ExcpAdjField.chiselType)
}

class Lsu extends Module {
  class Port extends Bundle {
    val msgIn  = Flipped(Decoupled(new Exu2LsuMsg))
    val msgOut = Decoupled(new Lsu2WbuMsg)
    val rReq   = Irrevocable(new MemReadReq(XLen.W))
    val rResp  = Flipped(Irrevocable(new MemReadResp(XLen.W)))
    val wReq   = Irrevocable(new MemWriteReq(XLen.W, 32.W))
    val wResp  = Flipped(Irrevocable(new MemWriteResp))
  }
  val io = IO(new Port)

  private val bad = Seq(
    io.msgIn.bits.ifuExcp,
    io.msgIn.bits.iduExcp
  ).map(_.asUInt.orR).reduce(_ | _)

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

  private val wEn = io.msgIn.bits.memAction === MemAction.MemWt
  private val rEn = io.msgIn.bits.memAction.isOneOf(MemAction.MemRd, MemAction.MemRdu)

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
    val S_Idle     = Value
    val S_ReadReq  = Value
    val S_Read     = Value
    val S_WriteReq = Value
    val S_Write    = Value
    val S_Done     = Value
  }
  import State._
  private val (firstAction, _) = State.safe(
    decoder(
      Cat(bad | alignBad, rEn, wEn),
      TruthTable(
        Seq(
          "b000".BP -> S_Done.BP,
          "b1??".BP -> S_Done.BP,
          "b01?".BP -> S_ReadReq.BP,
          "b001".BP -> S_WriteReq.BP
        ),
        S_Idle.BP
      )
    )
  )
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle     -> Mux(io.msgIn.valid, firstAction, S_Idle),
      S_ReadReq  -> Mux(io.rReq.ready, S_Read, S_ReadReq),
      S_Read     -> Mux(io.rResp.valid, Mux(wEn, S_WriteReq, S_Done), S_Read),
      S_WriteReq -> Mux(io.wReq.ready, S_Write, S_WriteReq),
      S_Write    -> Mux(io.wResp.valid, S_Done, S_Write),
      S_Done     -> Mux(io.msgOut.ready, S_Idle, S_Done)
    )
  )

  private val memRResp = RegEnable(io.rResp.bits.rResp, io.rResp.valid)
  private val memWResp = RegEnable(io.wResp.bits.bResp, io.wResp.valid)

  io.rReq.valid  := y === S_ReadReq
  io.rResp.ready := io.rResp.valid & y === S_Done

  io.wReq.valid  := y === S_WriteReq
  io.wResp.ready := io.wResp.valid & y === S_Done

  io.msgOut.bits.memRData := sext.io.sextRes

  io.msgOut.bits.lsuExcp.loadMisalign  := rEn & alignBad
  io.msgOut.bits.lsuExcp.loadAccess    := rEn & memRResp =/= RResp.Okay
  io.msgOut.bits.lsuExcp.storeMisalign := wEn & alignBad
  io.msgOut.bits.lsuExcp.storeAccess   := wEn & memWResp =/= BResp.Okay

  io.msgOut.bits.instr   := io.msgIn.bits.instr
  io.msgOut.bits.d       := io.msgIn.bits.d
  io.msgOut.bits.pc      := io.msgIn.bits.pc
  io.msgOut.bits.snpc    := io.msgIn.bits.snpc
  io.msgOut.bits.ifuExcp := io.msgIn.bits.ifuExcp
  io.msgOut.bits.iduExcp := io.msgIn.bits.iduExcp
  io.msgOut.bits.wbEn    := io.msgIn.bits.wbEn
  io.msgOut.bits.wbSel   := io.msgIn.bits.wbSel
  io.msgOut.bits.pcSel   := io.msgIn.bits.pcSel
  io.msgOut.bits.rdIdx   := io.msgIn.bits.rdIdx
  io.msgOut.bits.csrVal  := io.msgIn.bits.csrVal
  io.msgOut.bits.csrAddr := io.msgIn.bits.csrAddr
  io.msgOut.bits.csrWbEn := io.msgIn.bits.csrWbEn
  io.msgOut.bits.excpAdj := io.msgIn.bits.excpAdj

  io.msgIn.ready  := y === S_Idle & ~io.msgIn.valid
  io.msgOut.valid := y === S_Done
}
