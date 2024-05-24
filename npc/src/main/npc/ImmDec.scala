package npc

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

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

  val sign    = io.instr(31)
  val immR    = 0.U(XLen.W)
  val immI    = Cat(Fill(XLen - 12, sign), io.instr(31, 20))
  val immIs   = Cat(Fill(XLen - 6, false.B), io.instr(25, 20))
  val immIcsr = Cat(Fill(XLen - 12, false.B), io.instr(31, 20))
  val immS    = Cat(Fill(XLen - 12, sign), io.instr(31, 25), io.instr(11, 7))
  val immB    = Cat(Fill(XLen - 13, sign), io.instr(31), io.instr(7), io.instr(30, 25), io.instr(11, 8), false.B)
  val immU =
    if (XLen == 32) Cat(io.instr(31, 12), Fill(12, false.B))
    else Cat(Fill(XLen - 32, sign), io.instr(31, 12), Fill(12, false.B))
  val immJ = Cat(Fill(XLen - 21, sign), io.instr(31), io.instr(19, 12), io.instr(20), io.instr(30, 21), false.B)

  val table = TruthTable(
    Seq(
      ImmR.BP    -> "b000000001".BP,
      ImmI.BP    -> "b000000010".BP,
      ImmIs.BP   -> "b000000100".BP,
      ImmIcsr.BP -> "b000001000".BP,
      ImmS.BP    -> "b000010000".BP,
      ImmB.BP    -> "b000100000".BP,
      ImmU.BP    -> "b001000000".BP,
      ImmJ.BP    -> "b010000000".BP
    ),
    "b100000000".BP
  )
  val immFmt1H = decoder(io.immFmt, table)
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
