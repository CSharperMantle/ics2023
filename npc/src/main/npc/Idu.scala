package npc

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import common._

object Instr {
  object Type extends CvtChiselEnum {
    val TypeR    = Value
    val TypeI    = Value
    val TypeIs   = Value
    val TypeIcsr = Value
    val TypeS    = Value
    val TypeB    = Value
    val TypeU    = Value
    val TypeJ    = Value
  }

  object MemLen extends CvtChiselEnum {
    val LenB = Value
    val LenH = Value
    val LenW = Value
    val LenD = Value
  }

  object Opcode extends CvtChiselEnum {
    val ADDI = Value("b_00100_11".U)
    val Env  = Value("b_11100_11".U)
  }
}

import Instr._

case class InstrPat(
  val name:   String,
  val funct7: BitPat,
  val rs2:    BitPat,
  val rs1:    BitPat,
  val funct3: BitPat,
  val rd:     BitPat,
  val opcode: BitPat)
    extends DecodePattern {
  require(funct7.getWidth == 7)
  require(rs2.getWidth == 5)
  require(rs1.getWidth == 5)
  require(funct3.getWidth == 3)
  require(rd.getWidth == 5)
  require(opcode.getWidth == 7)

  def bitPat = pattern

  val pattern = funct7 ## rs2 ## rs1 ## funct3 ## rd ## opcode
}

object MemLenField extends DecodeField[InstrPat, UInt] {
  def name       = "memLen"
  def chiselType = UInt(MemLen.W)
  def genTable(pat: InstrPat): BitPat = {
    pat.funct3.rawString match {
      case "000" | "100" => MemLen.LenB.BP
      case "001" | "101" => MemLen.LenH.BP
      case "010" | "110" => MemLen.LenW.BP
      case _             => MemLen.LenD.BP
    }
  }
}

object BreakField extends BoolDecodeField[InstrPat] {
  def name = "break"
  def genTable(pat: InstrPat): BitPat = {
    if (pat.opcode.equals(Opcode.Env.BP) && pat.rs2.equals(BitPat("b_00001"))) y else n
  }
}

class IduIO extends Bundle {
  val instr  = Input(UInt(32.W))
  val break  = Output(Bool())
  val memLen = Output(UInt())
}

class Idu extends Module {
  val io = IO(new IduIO())

  def BitPatX(width: Int): BitPat = BitPat("b" + ("?" * width))
  def BitPatY(width: Int): BitPat = BitPat.Y(width)
  def BitPatN(width: Int): BitPat = BitPat.N(width)

  val patterns = Seq(
    // format: off
    InstrPat("addi",   BitPatX(7), BitPatX(5),        BitPatX(5), BitPatX(3), BitPatX(5), Opcode.ADDI.BP),
    InstrPat("ecall",  BitPatN(7), BitPatN(5),        BitPatN(5), BitPatN(3), BitPatN(5), Opcode.Env.BP),
    InstrPat("ebreak", BitPatN(7), BitPat("b_00001"), BitPatN(5), BitPatN(3), BitPatN(5), Opcode.Env.BP)
    // format: on
  )
  val fields = Seq(
    MemLenField,
    BreakField
  )
  val decoder      = new DecodeTable(patterns, fields)
  val decodeResult = decoder.decode(io.instr)

  io.break  := decodeResult(BreakField)
  io.memLen := decodeResult(MemLenField)
}
