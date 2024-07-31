package npc

import chisel3._
import chisel3.util._

import common._
import npc._

object ImmFmt extends CvtChiselEnum {
  val ImmR    = Value
  val ImmI    = Value
  val ImmIs   = Value
  val ImmIcsr = Value
  val ImmS    = Value
  val ImmB    = Value
  val ImmU    = Value
  val ImmJ    = Value
}

class ImmDecIO extends Bundle {
  val instr  = Input(UInt(32.W))
  val immFmt = Input(UInt(ImmFmt.W))
  val imm    = Output(UInt(XLen.W))
}

class ImmDec extends Module {
  import ImmFmt._

  val io = IO(new ImmDecIO)

  private val sign    = io.instr(31)
  private val immR    = 0.U(XLen.W)
  private val immI    = Cat(Fill(XLen - 12, sign), io.instr(31, 20))
  private val immIs   = Cat(Fill(XLen - 6, false.B), io.instr(25, 20))
  private val immIcsr = Cat(Fill(XLen - 12, false.B), io.instr(31, 20))
  private val immS    = Cat(Fill(XLen - 12, sign), io.instr(31, 25), io.instr(11, 7))
  private val immB = Cat(
    Fill(XLen - 13, sign),
    io.instr(31),
    io.instr(7),
    io.instr(30, 25),
    io.instr(11, 8),
    false.B
  )
  private val immU =
    if (XLen == 32) Cat(io.instr(31, 12), Fill(12, false.B))
    else Cat(Fill(XLen - 32, sign), io.instr(31, 12), Fill(12, false.B))
  private val immJ = Cat(
    Fill(XLen - 21, sign),
    io.instr(31),
    io.instr(19, 12),
    io.instr(20),
    io.instr(30, 21),
    false.B
  )

  private val dec = Decoder1H(
    Seq(
      ImmR.BP    -> 0,
      ImmI.BP    -> 1,
      ImmIs.BP   -> 2,
      ImmIcsr.BP -> 3,
      ImmS.BP    -> 4,
      ImmB.BP    -> 5,
      ImmU.BP    -> 6,
      ImmJ.BP    -> 7
    )
  )
  private val immFmt1H = dec(io.immFmt)
  io.imm := Mux1H(
    Seq(
      immFmt1H(0) -> immR,
      immFmt1H(1) -> immI,
      immFmt1H(2) -> immIs,
      immFmt1H(3) -> immIcsr,
      immFmt1H(4) -> immS,
      immFmt1H(5) -> immB,
      immFmt1H(6) -> immU,
      immFmt1H(7) -> immJ,
      immFmt1H(8) -> 0.U
    )
  )
}
