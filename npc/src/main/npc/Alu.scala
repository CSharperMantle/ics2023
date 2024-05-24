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
      AluCalcOp.Add.BP  -> "b000000001".BP,
      AluCalcOp.Sl.BP   -> "b000000010".BP,
      AluCalcOp.Slt.BP  -> "b000000100".BP,
      AluCalcOp.Sltu.BP -> "b000001000".BP,
      AluCalcOp.Xor.BP  -> "b000010000".BP,
      AluCalcOp.Sr.BP   -> "b000100000".BP,
      AluCalcOp.Or.BP   -> "b001000000".BP,
      AluCalcOp.And.BP  -> "b010000000".BP
    ),
    "b100000000".BP
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
      AluBrCond.Eq.BP  -> "b0000001".BP,
      AluBrCond.Ne.BP  -> "b0000010".BP,
      AluBrCond.Lt.BP  -> "b0000100".BP,
      AluBrCond.Ge.BP  -> "b0001000".BP,
      AluBrCond.Ltu.BP -> "b0010000".BP,
      AluBrCond.Geu.BP -> "b0100000".BP
    ),
    "b1000000".BP
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
