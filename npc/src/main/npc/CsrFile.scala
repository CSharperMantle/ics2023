package npc

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import common._
import npc._

object CsrInternalIdx extends CvtChiselEnum {
  val SatpIdx     = Value
  val MstatusIdx  = Value
  val MieIdx      = Value
  val MtvecIdx    = Value
  val MscratchIdx = Value
  val MepcIdx     = Value
  val McauseIdx   = Value
  val MtvalIdx    = Value
  val MipIdx      = Value
  val UnkIdx      = Value
}

object CsrOp extends CvtChiselEnum {
  val Rw  = Value
  val Rs  = Value
  val Rc  = Value
  val Unk = Value
}

class CsrFileIO extends Bundle {
  val csrIdx = Input(UInt(12.W))
  val csrOp  = Input(UInt(CsrOp.W))
  val s1     = Input(UInt(XLen.W))
  val csrVal = Output(UInt(XLen.W))
  val epc    = Output(UInt(XLen.W))
}

class CsrFile extends Module {
  import CsrOp._
  import CsrInternalIdx._

  val io = IO(new CsrFileIO)

  val csrs = Mem(1 << CsrInternalIdx.getWidth, UInt(npc.XLen.W))

  val csrOpDec = Decoder1H(
    Seq(
      Rw.BP -> 0,
      Rs.BP -> 1,
      Rc.BP -> 2
    )
  )
  val csrOp1H = csrOpDec(io.csrOp)

  val csrIdxDecTable = TruthTable(
    Seq(
      BitPat("h180".U(12.W)) -> SatpIdx.BP,
      BitPat("h300".U(12.W)) -> MstatusIdx.BP,
      BitPat("h304".U(12.W)) -> MieIdx.BP,
      BitPat("h305".U(12.W)) -> MtvecIdx.BP,
      BitPat("h340".U(12.W)) -> MscratchIdx.BP,
      BitPat("h341".U(12.W)) -> MepcIdx.BP,
      BitPat("h342".U(12.W)) -> McauseIdx.BP,
      BitPat("h343".U(12.W)) -> MtvalIdx.BP,
      BitPat("h344".U(12.W)) -> MipIdx.BP
    ),
    UnkIdx.BP
  )
  val csrIdxDecoded = decoder(io.csrIdx, csrIdxDecTable)

  val csrVal = Mux(csrIdxDecoded === UnkIdx.U, 0.U, csrs(csrIdxDecoded))
  csrs(csrIdxDecoded) := Mux1H(
    Seq(
      csrOp1H(0) -> io.s1,
      csrOp1H(1) -> (io.s1 | csrVal),
      csrOp1H(2) -> (~io.s1 & csrVal),
      csrOp1H(3) -> csrVal
    )
  )
  io.csrVal := csrVal

  io.epc := csrs(MepcIdx.U)
}
