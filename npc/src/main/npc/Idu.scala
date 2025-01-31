package npc

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import common._
import npc._

object InstrOpcodeBP {
  // -*- RV64I/RV32I: Integer base instructions -*-
  // INTEGER COMPUTATIONAL INSTRUCTIONS
  // Arithmetic
  val add   = "b01100_11".BP
  val addw  = "b01110_11".BP
  val addi  = "b00100_11".BP
  val addiw = "b00110_11".BP
  val sub   = "b01100_11".BP
  val subw  = "b01110_11".BP
  val lui   = "b01101_11".BP
  val auipc = "b00101_11".BP
  // Logical
  val xor  = "b01100_11".BP
  val xori = "b00100_11".BP
  val or   = "b01100_11".BP
  val ori  = "b00100_11".BP
  val and  = "b01100_11".BP
  val andi = "b00100_11".BP
  // Shifts
  val sll   = "b01100_11".BP
  val sllw  = "b01110_11".BP
  val slli  = "b00100_11".BP
  val slliw = "b00110_11".BP
  val srl   = "b01100_11".BP
  val srlw  = "b01110_11".BP
  val srli  = "b00100_11".BP
  val srliw = "b00110_11".BP
  val sra   = "b01100_11".BP
  val sraw  = "b01110_11".BP
  val srai  = "b00100_11".BP
  val sraiw = "b00110_11".BP
  // Compare
  val slt   = "b01100_11".BP
  val slti  = "b00100_11".BP
  val sltu  = "b01100_11".BP
  val sltiu = "b00100_11".BP
  // LOADS AND STORES
  // Loads
  val lb  = "b00000_11".BP
  val lh  = "b00000_11".BP
  val lw  = "b00000_11".BP
  val ld  = "b00000_11".BP
  val lbu = "b00000_11".BP
  val lhu = "b00000_11".BP
  val lwu = "b00000_11".BP
  // Stores
  val sb = "b01000_11".BP
  val sh = "b01000_11".BP
  val sw = "b01000_11".BP
  val sd = "b01000_11".BP
  // CONTROL TRANSFERS
  // Branches
  val beq  = "b11000_11".BP
  val bne  = "b11000_11".BP
  val blt  = "b11000_11".BP
  val bge  = "b11000_11".BP
  val bltu = "b11000_11".BP
  val bgeu = "b11000_11".BP
  // Jump & Link
  val jal  = "b11011_11".BP
  val jalr = "b11001_11".BP
  // ENVIRONMENTAL CALLS & BREAKPOINTS
  // System
  val ecall  = "b11100_11".BP
  val ebreak = "b11100_11".BP
  // Trap-Return
  val mret = "b11100_11".BP
  // Memory Fencing
  val fencei = "b00011_11".BP
  // -*- RV64M/RV32M: Integer multiplication and division -*-
  val mul   = "b01100_11".BP
  val mulh  = "b01100_11".BP
  val mulhu = "b01100_11".BP
  val mulw  = "b01110_11".BP
  val div   = "b01100_11".BP
  val divw  = "b01110_11".BP
  val divu  = "b01100_11".BP
  val divuw = "b01110_11".BP
  val rem   = "b01100_11".BP
  val remw  = "b01110_11".BP
  val remu  = "b01100_11".BP
  val remuw = "b01110_11".BP
  // -*- RV64Zicsr/RV32Zicsr: Control and status register (CSR), v
  val csrrw  = "b11100_11".BP
  val csrrwi = "b11100_11".BP
  val csrrs  = "b11100_11".BP
  val csrrsi = "b11100_11".BP
  val csrrc  = "b11100_11".BP
  val csrrci = "b11100_11".BP
}

object AluOpSel extends Enumeration {
  val AluOpFunct3 = Value
  val AluOpAdd    = Value
  val AluOpX      = Value
}

case class InstrPat(
  val funct7:     BitPat,
  val rs2:        BitPat,
  val rs1:        BitPat,
  val funct3:     BitPat,
  val rd:         BitPat,
  val opcode:     BitPat,
  val immFmt:     BitPat,
  val pcSel:      BitPat,
  val srcASel:    BitPat,
  val srcBSel:    BitPat,
  val aluOpSel:   AluOpSel.Value,
  val memAction:  BitPat,
  val wbSel:      BitPat,
  val wbEn:       BitPat,
  val excpAdj:    BitPat,
  val cacheFlush: BitPat)
    extends DecodePattern {
  require(funct7.getWidth == 7)
  require(rs2.getWidth == 5)
  require(rs1.getWidth == 5)
  require(funct3.getWidth == 3)
  require(rd.getWidth == 5)
  require(opcode.getWidth == 7)

  override def bitPat = funct7 ## rs2 ## rs1 ## funct3 ## rd ## opcode
}

object BreakField extends BoolDecodeField[InstrPat] {
  override def name = "break"
  override def genTable(pat: InstrPat): BitPat = {
    if ((pat.opcode == InstrOpcodeBP.ebreak) && (pat.rs2 == "b00001".BP)) y
    else n
  }
}

object MemActionField extends DecodeField[InstrPat, MemAction.Type] {
  override def name       = "memAction"
  override def chiselType = MemAction()
  override def genTable(pat: InstrPat): BitPat = pat.memAction
}

object MemWidthField extends DecodeField[InstrPat, MemWidth.Type] {
  override def name       = "memWidth"
  override def chiselType = MemWidth()
  override def genTable(pat: InstrPat): BitPat = {
    pat.funct3.rawString match {
      case "000" | "100" => MemWidth.LenB.BP
      case "001" | "101" => MemWidth.LenH.BP
      case "010" | "110" => MemWidth.LenW.BP
      case _             => MemWidth.LenD.BP
    }
  }
}

object AluCalcOpField extends DecodeField[InstrPat, UInt] {
  override def name       = "aluCalcOp"
  override def chiselType = UInt(AluCalcOp.W)
  override def genTable(pat: InstrPat): BitPat = {
    pat.aluOpSel match {
      case AluOpSel.AluOpFunct3 =>
        pat.funct3.rawString match {
          case "001" => AluCalcOp.Sl.BP
          case "010" => AluCalcOp.Slt.BP
          case "011" => AluCalcOp.Sltu.BP
          case "100" => AluCalcOp.Xor.BP
          case "101" => AluCalcOp.Sr.BP
          case "110" => AluCalcOp.Or.BP
          case "111" => AluCalcOp.And.BP
          case _     => AluCalcOp.Add.BP // this could make ??? defaults to add.
        }
      case AluOpSel.AluOpAdd => AluCalcOp.Add.BP
      case AluOpSel.AluOpX   => AluCalcOp.X
    }
  }
}

object AluCalcDirField extends DecodeField[InstrPat, AluCalcDir.Type] {
  override def name       = "aluCalcDir"
  override def chiselType = AluCalcDir()
  override def genTable(pat: InstrPat): BitPat = {
    pat.funct7.rawString(1) match {
      case '0' => AluCalcDir.Pos.BP
      case '1' => AluCalcDir.Neg.BP
      case _   => AluCalcDir.Pos.BP
    }
  }
}

object AluBrCondField extends DecodeField[InstrPat, AluBrCond.Type] {
  override def name       = "aluBrCond"
  override def chiselType = AluBrCond()
  override def genTable(pat: InstrPat): BitPat = {
    pat.funct3.rawString match {
      case "000" => AluBrCond.Eq.BP
      case "001" => AluBrCond.Ne.BP
      case "100" => AluBrCond.Lt.BP
      case "101" => AluBrCond.Ge.BP
      case "110" => AluBrCond.Ltu.BP
      case "111" => AluBrCond.Geu.BP
      case _     => AluBrCond.Unk.BP
    }
  }
}

object CsrOpField extends DecodeField[InstrPat, CsrOp.Type] {
  override def name       = "csrOp"
  override def chiselType = CsrOp()
  override def genTable(pat: InstrPat): BitPat = {
    import CsrOp._
    if (pat.wbSel == WbSel.WbCsr.BP)
      pat.funct3.rawString match {
        case "001" | "101" => Rw.BP
        case "010" | "110" => Rs.BP
        case "011" | "111" => Rc.BP
        case _             => Unk.BP
      }
    else Unk.BP
  }
}

object ImmFmtField extends DecodeField[InstrPat, ImmFmt.Type] {
  override def name       = "immFmt"
  override def chiselType = ImmFmt()
  override def genTable(pat: InstrPat): BitPat = pat.immFmt
}

object PcSelField extends DecodeField[InstrPat, PcSel.Type] {
  override def name       = "pcSel"
  override def chiselType = PcSel()
  override def genTable(pat: InstrPat): BitPat = pat.pcSel
}

object SrcASelField extends DecodeField[InstrPat, ExSrcASel.Type] {
  override def name       = "srcASel"
  override def chiselType = ExSrcASel()
  override def genTable(pat: InstrPat): BitPat = pat.srcASel
}

object SrcBSelField extends DecodeField[InstrPat, ExSrcBSel.Type] {
  override def name       = "srcBSel"
  override def chiselType = ExSrcBSel()
  override def genTable(pat: InstrPat): BitPat = pat.srcBSel
}

object WbSelField extends DecodeField[InstrPat, WbSel.Type] {
  override def name       = "wbSel"
  override def chiselType = WbSel()
  override def genTable(pat: InstrPat): BitPat = pat.wbSel
}

object WbEnField extends BoolDecodeField[InstrPat] {
  override def name = "wbEn"
  override def genTable(pat: InstrPat): BitPat = pat.wbEn
}

object ExcpAdjField extends DecodeField[InstrPat, CsrExcpAdj.Type] {
  override def name       = "excpAdj"
  override def chiselType = CsrExcpAdj()
  override def genTable(pat: InstrPat): BitPat = pat.excpAdj
}

object CacheFlushField extends BoolDecodeField[InstrPat] {
  override def name = "cacheFlush"
  override def genTable(pat: InstrPat): BitPat = pat.cacheFlush
}

class Idu2ExuMsg extends Bundle {
  // GEN
  // Used by Exu
  val rs1Idx     = Output(UInt(5.W))
  val rs2Idx     = Output(UInt(5.W))
  val aluCalcOp  = Output(AluCalcOpField.chiselType)
  val aluCalcDir = Output(AluCalcDirField.chiselType)
  val aluBrCond  = Output(AluBrCondField.chiselType)
  val csrOp      = Output(CsrOpField.chiselType)
  val imm        = Output(UInt(XLen.W))
  val srcASel    = Output(SrcASelField.chiselType)
  val srcBSel    = Output(SrcBSelField.chiselType)
  val excpAdj    = Output(ExcpAdjField.chiselType)
  val bad        = Output(Bool())
  // Unused by Exu
  val memAction = Output(MemActionField.chiselType)
  val memWidth  = Output(MemWidthField.chiselType)
  val wbEn      = Output(WbEnField.chiselType)
  val wbSel     = Output(WbSelField.chiselType)
  val rdIdx     = Output(UInt(5.W))
  val pcSel     = Output(PcSelField.chiselType)
  val break     = Output(Bool())
  // PASS-THRU
  val instr = Output(UInt(XLen.W))
  val pc    = Output(UInt(XLen.W))
  val snpc  = Output(UInt(XLen.W))
}

class Idu extends Module {
  class Port extends Bundle {
    val msgIn  = Flipped(Decoupled(new Ifu2IduMsg))
    val msgOut = Decoupled(new Idu2ExuMsg)

    val cacheFlush = Output(Bool())
  }
  val io = IO(new Port)

  import InstrOpcodeBP._
  import AluOpSel._
  import ImmFmt._
  import MemAction._
  import PcSel._
  import ExSrcASel._
  import ExSrcBSel._
  import WbSel._
  import CsrExcpAdj._

  private val patterns = Seq(
    // scalafmt: { maxColumn = 512, align.tokens.add = [ { code = "," } ] }
    //      |funct7        |rs2         |rs1   |funct3    |rd    |op     |Fmt        |PcSel      |SrcASel     |SrcBSel    |AluOpSel    |MemAct     |WbSel     |WbEn  |ExcpAdj         |CacheFlush
    InstrPat("b0000000".BP, 5.W.X,       5.W.X, "b000".BP, 5.W.X, add,    ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b000".BP, 5.W.X, addi,   ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat("b0100000".BP, 5.W.X,       5.W.X, "b000".BP, 5.W.X, sub,    ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, 3.W.X,     5.W.X, lui,    ImmU.BP,    PcSnpc.BP,  SrcAR0.BP,   SrcBImm.BP, AluOpAdd,    MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, 3.W.X,     5.W.X, auipc,  ImmU.BP,    PcSnpc.BP,  SrcAPc.BP,   SrcBImm.BP, AluOpAdd,    MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.N,         5.W.X,       5.W.X, "b100".BP, 5.W.X, xor,    ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b100".BP, 5.W.X, xori,   ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.N,         5.W.X,       5.W.X, "b110".BP, 5.W.X, or,     ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b110".BP, 5.W.X, ori,    ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.N,         5.W.X,       5.W.X, "b111".BP, 5.W.X, and,    ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b111".BP, 5.W.X, andi,   ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat("b0000000".BP, 5.W.X,       5.W.X, "b001".BP, 5.W.X, sll,    ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat("b000000?".BP, 5.W.X,       5.W.X, "b001".BP, 5.W.X, slli,   ImmIs.BP,   PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat("b0000000".BP, 5.W.X,       5.W.X, "b101".BP, 5.W.X, srl,    ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat("b000000?".BP, 5.W.X,       5.W.X, "b101".BP, 5.W.X, srli,   ImmIs.BP,   PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat("b0100000".BP, 5.W.X,       5.W.X, "b101".BP, 5.W.X, sra,    ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat("b010000?".BP, 5.W.X,       5.W.X, "b101".BP, 5.W.X, srai,   ImmIs.BP,   PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.N,         5.W.X,       5.W.X, "b010".BP, 5.W.X, slt,    ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b010".BP, 5.W.X, slti,   ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.N,         5.W.X,       5.W.X, "b011".BP, 5.W.X, sltu,   ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b011".BP, 5.W.X, sltiu,  ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b000".BP, 5.W.X, lb,     ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpAdd,    MemRd.BP,   WbMem.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b001".BP, 5.W.X, lh,     ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpAdd,    MemRd.BP,   WbMem.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b010".BP, 5.W.X, lw,     ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpAdd,    MemRd.BP,   WbMem.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b100".BP, 5.W.X, lbu,    ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpAdd,    MemRdu.BP,  WbMem.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b101".BP, 5.W.X, lhu,    ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpAdd,    MemRdu.BP,  WbMem.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b000".BP, 5.W.X, sb,     ImmS.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpAdd,    MemWt.BP,   WbSel.X,   1.W.N, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b001".BP, 5.W.X, sh,     ImmS.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpAdd,    MemWt.BP,   WbSel.X,   1.W.N, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b010".BP, 5.W.X, sw,     ImmS.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpAdd,    MemWt.BP,   WbSel.X,   1.W.N, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b000".BP, 5.W.X, beq,    ImmB.BP,    PcBr.BP,    SrcARs1.BP,  SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.W.N, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b001".BP, 5.W.X, bne,    ImmB.BP,    PcBr.BP,    SrcARs1.BP,  SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.W.N, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b100".BP, 5.W.X, blt,    ImmB.BP,    PcBr.BP,    SrcARs1.BP,  SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.W.N, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b101".BP, 5.W.X, bge,    ImmB.BP,    PcBr.BP,    SrcARs1.BP,  SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.W.N, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b110".BP, 5.W.X, bltu,   ImmB.BP,    PcBr.BP,    SrcARs1.BP,  SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.W.N, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b111".BP, 5.W.X, bgeu,   ImmB.BP,    PcBr.BP,    SrcARs1.BP,  SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.W.N, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, 3.W.X,     5.W.X, jal,    ImmJ.BP,    PcAlu.BP,   SrcAPc.BP,   SrcBImm.BP, AluOpFunct3, MemNone.BP, WbSnpc.BP, 1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, 3.W.N,     5.W.X, jalr,   ImmI.BP,    PcAlu.BP,   SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbSnpc.BP, 1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.N,         5.W.N,       5.W.N, 3.W.N,     5.W.N, ecall,  ImmI.BP,    PcMtvec.BP, SrcAR0.BP,   SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.W.N, ExcpAdjEcall.BP, 1.W.N),
    InstrPat(7.W.N,         "b00001".BP, 5.W.N, 3.W.N,     5.W.N, ebreak, ImmI.BP,    PcSnpc.BP,  SrcAR0.BP,   SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.W.N, ExcpAdjNone.BP,  1.W.N),
    InstrPat("b0011000".BP, "b00010".BP, 5.W.N, 3.W.N,     5.W.N, mret,   ImmR.BP,    PcMepc.BP,  SrcAR0.BP,   SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.W.N, ExcpAdjMret.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b001".BP, 5.W.X, csrrw,  ImmIcsr.BP, PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpX,      MemNone.BP, WbCsr.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b010".BP, 5.W.X, csrrs,  ImmIcsr.BP, PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpX,      MemNone.BP, WbCsr.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b011".BP, 5.W.X, csrrc,  ImmIcsr.BP, PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpX,      MemNone.BP, WbCsr.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b101".BP, 5.W.X, csrrwi, ImmIcsr.BP, PcSnpc.BP,  SrcAZimm.BP, SrcBImm.BP, AluOpX,      MemNone.BP, WbCsr.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b110".BP, 5.W.X, csrrsi, ImmIcsr.BP, PcSnpc.BP,  SrcAZimm.BP, SrcBImm.BP, AluOpX,      MemNone.BP, WbCsr.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.X,         5.W.X,       5.W.X, "b111".BP, 5.W.X, csrrci, ImmIcsr.BP, PcSnpc.BP,  SrcAZimm.BP, SrcBImm.BP, AluOpX,      MemNone.BP, WbCsr.BP,  1.W.Y, ExcpAdjNone.BP,  1.W.N),
    InstrPat(7.W.N,         5.W.N,       5.W.N, "b001".BP, 5.W.N, fencei, ImmI.BP,    PcSnpc.BP,  SrcAR0.BP,   SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.W.N, ExcpAdjNone.BP,  1.W.Y)
    // scalafmt: { align.tokens.add = [] }
  )
  private val fields = Seq(
    BreakField,
    MemWidthField,
    AluCalcOpField,
    AluCalcDirField,
    AluBrCondField,
    CsrOpField,
    ImmFmtField,
    PcSelField,
    SrcASelField,
    SrcBSelField,
    MemActionField,
    WbSelField,
    WbEnField,
    ExcpAdjField,
    CacheFlushField
  )
  private val table = new DecodeTable(patterns, fields)
  private val res   = table.decode(io.msgIn.bits.instr)

  private val immDec = Module(new ImmDec)
  immDec.io.instr  := io.msgIn.bits.instr
  immDec.io.immFmt := res(ImmFmtField)

  io.msgOut.bits.rs1Idx     := io.msgIn.bits.instr(19, 15)
  io.msgOut.bits.rs2Idx     := io.msgIn.bits.instr(24, 20)
  io.msgOut.bits.aluCalcOp  := res(AluCalcOpField)
  io.msgOut.bits.aluCalcDir := res(AluCalcDirField)
  io.msgOut.bits.aluBrCond  := res(AluBrCondField)
  io.msgOut.bits.csrOp      := res(CsrOpField)
  io.msgOut.bits.imm        := immDec.io.imm
  io.msgOut.bits.srcASel    := res(SrcASelField)
  io.msgOut.bits.srcBSel    := res(SrcBSelField)
  io.msgOut.bits.excpAdj    := res(ExcpAdjField)
  io.msgOut.bits.bad        := io.msgIn.bits.bad | io.msgIn.bits.instr(1, 0) =/= "b11".U

  io.msgOut.bits.memAction := res(MemActionField)
  io.msgOut.bits.memWidth  := res(MemWidthField)
  io.msgOut.bits.wbEn      := res(WbEnField)
  io.msgOut.bits.wbSel     := res(WbSelField)
  io.msgOut.bits.rdIdx     := io.msgIn.bits.instr(11, 7)
  io.msgOut.bits.pcSel     := res(PcSelField)
  io.msgOut.bits.break     := res(BreakField)

  io.msgOut.bits.instr := io.msgIn.bits.instr
  io.msgOut.bits.pc    := io.msgIn.bits.pc
  io.msgOut.bits.snpc  := io.msgIn.bits.snpc

  io.cacheFlush := res(CacheFlushField)

  io.msgIn.ready  := io.msgOut.ready
  io.msgOut.valid := io.msgIn.valid
}
