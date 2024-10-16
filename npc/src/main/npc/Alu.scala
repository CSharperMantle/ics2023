package npc

import chisel3._
import chisel3.util._

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
class Alu extends Module {
  class Port extends Bundle {
    val calcOp  = Input(UInt(AluCalcOp.W))
    val calcDir = Input(UInt(AluCalcDir.W))
    val brCond  = Input(UInt(AluBrCond.W))
    val s1      = Input(UInt(XLen.W))
    val s2      = Input(UInt(XLen.W))
    val d       = Output(UInt(XLen.W))
    val brTaken = Output(Bool())
    val brBad   = Output(Bool())
  }
  val io = IO(new Port)

  private val sum  = io.s1 + io.s2
  private val diff = io.s1 + (-io.s2)
  private val add  = Mux(io.calcDir === AluCalcDir.Pos.U, sum, diff)

  private val neq   = diff.orR
  private val sigEq = io.s1(XLen - 1) === io.s2(XLen - 1)
  private val lt    = Mux(sigEq, diff(XLen - 1), io.s1(XLen - 1))
  private val ltu   = Mux(sigEq, diff(XLen - 1), io.s2(XLen - 1))

  // Wang, W., & Xing, J. "CPU Design and Practice", p.87.
  private val shamt     = (if (XLen == 32) io.s2(4, 0) else io.s2(5, 0)).asUInt
  private val shSrc     = Mux(io.calcOp === AluCalcOp.Sl.U, Reverse(io.s1), io.s1)
  private val shRes     = (shSrc >> shamt)(XLen - 1, 0)
  private val shMaskSra = ~(~0.U(XLen.W) >> shamt)(XLen - 1, 0)
  private val srl       = shRes
  private val sra       = (Fill(XLen, io.s1(XLen - 1)) & shMaskSra) | shRes
  private val shr       = Mux(io.calcDir === AluCalcDir.Pos.U, srl, sra)
  private val shl       = Reverse(shRes)

  private val calcOpDec = Decoder1H(
    Seq(
      AluCalcOp.Add.BP  -> 0,
      AluCalcOp.Sl.BP   -> 1,
      AluCalcOp.Slt.BP  -> 2,
      AluCalcOp.Sltu.BP -> 3,
      AluCalcOp.Xor.BP  -> 4,
      AluCalcOp.Sr.BP   -> 5,
      AluCalcOp.Or.BP   -> 6,
      AluCalcOp.And.BP  -> 7
    )
  )
  private val calcOp1H = calcOpDec(io.calcOp)
  io.d := Mux1H(
    Seq(
      calcOp1H(0) -> add,
      calcOp1H(1) -> shl,
      calcOp1H(2) -> lt,
      calcOp1H(3) -> ltu,
      calcOp1H(4) -> (io.s1 ^ io.s2),
      calcOp1H(5) -> shr,
      calcOp1H(6) -> (io.s1 | io.s2),
      calcOp1H(7) -> (io.s1 & io.s2),
      calcOp1H(8) -> 0.U
    )
  )

  private val brOpDec = Decoder1H(
    Seq(
      AluBrCond.Eq.BP  -> 0,
      AluBrCond.Ne.BP  -> 1,
      AluBrCond.Lt.BP  -> 2,
      AluBrCond.Ge.BP  -> 3,
      AluBrCond.Ltu.BP -> 4,
      AluBrCond.Geu.BP -> 5
    )
  )
  private val brOp1H = brOpDec(io.brCond)
  io.brTaken := Mux1H(
    Seq(
      brOp1H(0) -> ~neq,
      brOp1H(1) -> neq,
      brOp1H(2) -> lt,
      brOp1H(3) -> ~lt,
      brOp1H(4) -> ltu,
      brOp1H(5) -> ~ltu,
      brOp1H(6) -> 0.U
    )
  )
  io.brBad := brOp1H(6)
}
