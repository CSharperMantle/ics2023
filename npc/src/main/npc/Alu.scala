package npc

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import common._
import npc._

object AluCalcOp extends CvtChiselEnum {
  val Add  = Value
  val Sl   = Value
  val Slt  = Value
  val Sltu = Value
  val Xor  = Value
  val Sr   = Value
  val Or   = Value
  val And  = Value
}

object AluCalcDir extends CvtChiselEnum {
  val Pos = Value
  val Neg = Value
}

object AluBrCond extends CvtChiselEnum {
  val Eq  = Value
  val Ne  = Value
  val Lt  = Value
  val Ge  = Value
  val Ltu = Value
  val Geu = Value
  val Unk = Value
}

class AluIO extends Bundle {
  val calcOp    = Input(UInt(AluCalcOp.W))
  val calcDir   = Input(UInt(AluCalcDir.W))
  val brCond    = Input(UInt(AluBrCond.W))
  val s1        = Input(UInt(XLen.W))
  val s2        = Input(UInt(XLen.W))
  val d         = Output(UInt(XLen.W))
  val brTaken   = Output(Bool())
  val brInvalid = Output(Bool())
}

class Alu extends Module {
  val io = IO(new AluIO)

  val sum  = io.s1 + io.s2
  val diff = io.s1 + (-io.s2)
  val add  = Mux(io.calcDir === AluCalcDir.Pos.U, sum, diff)

  val neq   = diff.orR
  val sigEq = io.s1(XLen - 1) === io.s2(XLen - 1)
  val lt    = Mux(sigEq, diff(XLen - 1), io.s1(XLen - 1))
  val ltu   = Mux(sigEq, diff(XLen - 1), io.s2(XLen - 1))

  // Wang, W., & Xing, J. "CPU Design and Practice", p.87.
  val shamt     = (if (XLen == 32) io.s2(4, 0) else io.s2(5, 0)).asUInt
  val shSrc     = Mux(io.calcOp === AluCalcOp.Sl.U, Reverse(io.s1), io.s1)
  val shRes     = (shSrc >> shamt)(XLen - 1, 0)
  val shMaskSra = ~(~0.U(XLen.W) >> shamt)(XLen - 1, 0)
  val srl       = shRes
  val sra       = (Fill(XLen, io.s1(XLen - 1)) & shMaskSra) | shRes
  val shr       = Mux(io.calcDir === AluCalcDir.Pos.U, srl, sra)
  val shl       = Reverse(shRes)

  val calcTable = TruthTable(
    Seq(
      AluCalcOp.Add.BP  -> BitPat("b_000000001"),
      AluCalcOp.Sl.BP   -> BitPat("b_000000010"),
      AluCalcOp.Slt.BP  -> BitPat("b_000000100"),
      AluCalcOp.Sltu.BP -> BitPat("b_000001000"),
      AluCalcOp.Xor.BP  -> BitPat("b_000010000"),
      AluCalcOp.Sr.BP   -> BitPat("b_000100000"),
      AluCalcOp.Or.BP   -> BitPat("b_001000000"),
      AluCalcOp.And.BP  -> BitPat("b_010000000")
    ),
    BitPat("b_100000000")
  )
  val calcOp1Hot = decoder(io.calcOp, calcTable)
  io.d := Mux1H(
    Seq(
      calcOp1Hot(0) -> add,
      calcOp1Hot(1) -> shl,
      calcOp1Hot(2) -> lt,
      calcOp1Hot(3) -> ltu,
      calcOp1Hot(4) -> (io.s1 ^ io.s2),
      calcOp1Hot(5) -> shr,
      calcOp1Hot(6) -> (io.s1 | io.s2),
      calcOp1Hot(7) -> (io.s1 & io.s2),
      calcOp1Hot(8) -> 0.U
    )
  )

  val brTable = TruthTable(
    Seq(
      AluBrCond.Eq.BP  -> BitPat("b_0000001"),
      AluBrCond.Ne.BP  -> BitPat("b_0000010"),
      AluBrCond.Lt.BP  -> BitPat("b_0000100"),
      AluBrCond.Ge.BP  -> BitPat("b_0001000"),
      AluBrCond.Ltu.BP -> BitPat("b_0010000"),
      AluBrCond.Geu.BP -> BitPat("b_0100000")
    ),
    BitPat("b_1000000")
  )
  val brOp1Hot = decoder(io.brCond, brTable)
  io.brTaken := Mux1H(
    Seq(
      brOp1Hot(0) -> ~neq,
      brOp1Hot(1) -> neq,
      brOp1Hot(2) -> lt,
      brOp1Hot(3) -> ~lt,
      brOp1Hot(4) -> ltu,
      brOp1Hot(5) -> ~ltu,
      brOp1Hot(6) -> 0.U
    )
  )
  io.brInvalid := brOp1Hot(6)
}
