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
    val calcOp  = Input(AluCalcOp())
    val calcDir = Input(AluCalcDir())
    val brCond  = Input(AluBrCond())
    val s1      = Input(UInt(XLen.W))
    val s2      = Input(UInt(XLen.W))
    val d       = Output(UInt(XLen.W))
    val brTaken = Output(Bool())
  }
  val io = IO(new Port)

  private val sum  = io.s1 + io.s2
  private val diff = io.s1 + (-io.s2)
  private val add  = Mux(io.calcDir === AluCalcDir.Pos, sum, diff)

  private val neq   = diff.orR
  private val sigEq = io.s1(XLen - 1) === io.s2(XLen - 1)
  private val lt    = Mux(sigEq, diff(XLen - 1), io.s1(XLen - 1))
  private val ltu   = Mux(sigEq, diff(XLen - 1), io.s2(XLen - 1))

  // Wang, W., & Xing, J. "CPU Design and Practice", p.87.
  private val shamt     = (if (XLen == 32) io.s2(4, 0) else io.s2(5, 0)).asUInt
  private val shSrc     = Mux(io.calcOp === AluCalcOp.Sl, Reverse(io.s1), io.s1)
  private val shRes     = (shSrc >> shamt)(XLen - 1, 0)
  private val shMaskSra = ~(~0.U(XLen.W) >> shamt)(XLen - 1, 0)
  private val srl       = shRes
  private val sra       = (Fill(XLen, io.s1(XLen - 1)) & shMaskSra) | shRes
  private val shr       = Mux(io.calcDir === AluCalcDir.Pos, srl, sra)
  private val shl       = Reverse(shRes)

  io.d := MuxLookup(io.calcOp, 0.U)(
    Seq(
      AluCalcOp.Add  -> add,
      AluCalcOp.Sl   -> shl,
      AluCalcOp.Slt  -> lt,
      AluCalcOp.Sltu -> ltu,
      AluCalcOp.Xor  -> (io.s1 ^ io.s2),
      AluCalcOp.Sr   -> shr,
      AluCalcOp.Or   -> (io.s1 | io.s2),
      AluCalcOp.And  -> (io.s1 & io.s2)
    )
  )

  io.brTaken := MuxLookup(io.brCond, 0.U)(
    Seq(
      AluBrCond.Eq  -> ~neq,
      AluBrCond.Ne  -> neq,
      AluBrCond.Lt  -> lt,
      AluBrCond.Ge  -> ~lt,
      AluBrCond.Ltu -> ltu,
      AluBrCond.Geu -> ~ltu
    )
  )
}
