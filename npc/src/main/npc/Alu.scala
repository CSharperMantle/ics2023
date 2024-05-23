package npc

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import common._
import npc._

object AluOp extends CvtChiselEnum {
  val Add  = Value
  val Sl   = Value
  val Slt  = Value
  val Sltu = Value
  val Xor  = Value
  val Sr   = Value
  val Or   = Value
  val And  = Value
}

object AluDir extends CvtChiselEnum {
  val Pos = Value
  val Neg = Value
}

class AluIO extends Bundle {
  val i_op  = Input(UInt(AluOp.getWidth.W))
  val i_dir = Input(UInt(AluDir.getWidth.W))
  val i_s1  = Input(UInt(XLen.W))
  val i_s2  = Input(UInt(XLen.W))
  val o_d   = Output(UInt(XLen.W))
}

class Alu extends Module {
  val io = IO(new AluIO)

  val sum  = io.i_s1 + io.i_s2
  val diff = io.i_s1 + (-io.i_s2)
  val add  = Mux(io.i_dir === AluDir.Pos.U, sum, diff)

  // Warren, Henry S., Jr. "Hacker's Delight", 2nd ed, p.23.
  val lt  = (diff ^ ((io.i_s1 ^ io.i_s2) & (diff ^ io.i_s1))) >> (XLen - 1)
  val ltu = ((~io.i_s1 & io.i_s2) | ((~io.i_s1 | io.i_s2) & diff)) >> (XLen - 1)

  val shamt       = (if (XLen == 32) io.i_s2(4, 0) else io.i_s1(5, 0)).asUInt
  val sh_src      = Mux(io.i_op === AluOp.Sl.U, Reverse(io.i_s1), io.i_s1)
  val sh_res      = (sh_src >> shamt)(XLen - 1, 0)
  val sh_mask_sra = ~(~0.U(XLen.W) >> shamt)(XLen - 1, 0)
  val srl         = sh_res
  val sra         = (Fill(XLen, io.i_s1(XLen - 1)) & sh_mask_sra) | sh_res
  val shr         = Mux(io.i_dir === AluDir.Pos.U, srl, sra)
  val shl         = Reverse(sh_res)

  val table = TruthTable(
    Seq(
      AluOp.Add.BP  -> BitPat("b_000000001"),
      AluOp.Sl.BP   -> BitPat("b_000000010"),
      AluOp.Slt.BP  -> BitPat("b_000000100"),
      AluOp.Sltu.BP -> BitPat("b_000001000"),
      AluOp.Xor.BP  -> BitPat("b_000010000"),
      AluOp.Sr.BP   -> BitPat("b_000100000"),
      AluOp.Or.BP   -> BitPat("b_001000000"),
      AluOp.And.BP  -> BitPat("b_010000000")
    ),
    BitPat("b_100000000")
  )
  val res = Wire(UInt(9.W))
  res := decoder(io.i_op, table)

  io.o_d := Mux1H(
    Seq(
      res(0) -> add,
      res(1) -> shl,
      res(2) -> lt,
      res(3) -> ltu,
      res(4) -> (io.i_s1 ^ io.i_s2),
      res(5) -> shr,
      res(6) -> (io.i_s1 | io.i_s2),
      res(7) -> (io.i_s1 & io.i_s2),
      res(8) -> 0.U
    )
  )
}
