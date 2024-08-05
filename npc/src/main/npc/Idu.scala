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
  val Add   = "b01100_11".BP
  val Addw  = "b01110_11".BP
  val Addi  = "b00100_11".BP
  val Addiw = "b00110_11".BP
  val Sub   = "b01100_11".BP
  val Subw  = "b01110_11".BP
  val Lui   = "b01101_11".BP
  val Auipc = "b00101_11".BP
  // Logical
  val Xor  = "b01100_11".BP
  val Xori = "b00100_11".BP
  val Or   = "b01100_11".BP
  val Ori  = "b00100_11".BP
  val And  = "b01100_11".BP
  val Andi = "b00100_11".BP
  // Shifts
  val Sll   = "b01100_11".BP
  val Sllw  = "b01110_11".BP
  val Slli  = "b00100_11".BP
  val Slliw = "b00110_11".BP
  val Srl   = "b01100_11".BP
  val Srlw  = "b01110_11".BP
  val Srli  = "b00100_11".BP
  val Srliw = "b00110_11".BP
  val Sra   = "b01100_11".BP
  val Sraw  = "b01110_11".BP
  val Srai  = "b00100_11".BP
  val Sraiw = "b00110_11".BP
  // Compare
  val Slt   = "b01100_11".BP
  val Slti  = "b00100_11".BP
  val Sltu  = "b01100_11".BP
  val Sltiu = "b00100_11".BP
  // LOADS AND STORES
  // Loads
  val Lb  = "b00000_11".BP
  val Lh  = "b00000_11".BP
  val Lw  = "b00000_11".BP
  val Ld  = "b00000_11".BP
  val Lbu = "b00000_11".BP
  val Lhu = "b00000_11".BP
  val Lwu = "b00000_11".BP
  // Stores
  val Sb = "b01000_11".BP
  val Sh = "b01000_11".BP
  val Sw = "b01000_11".BP
  val Sd = "b01000_11".BP
  // CONTROL TRANSFERS
  // Branches
  val Beq  = "b11000_11".BP
  val Bne  = "b11000_11".BP
  val Blt  = "b11000_11".BP
  val Bge  = "b11000_11".BP
  val Bltu = "b11000_11".BP
  val Bgeu = "b11000_11".BP
  // Jump & Link
  val Jal  = "b11011_11".BP
  val Jalr = "b11001_11".BP
  // ENVIRONMENTAL CALLS & BREAKPOINTS
  // System
  val Ecall  = "b11100_11".BP
  val Ebreak = "b11100_11".BP
  // Trap-Return
  val Mret = "b11100_11".BP
  // -*- RV64M/RV32M: Integer multiplication and division -*-
  val Mul   = "b01100_11".BP
  val Mulh  = "b01100_11".BP
  val Mulhu = "b01100_11".BP
  val Mulw  = "b01110_11".BP
  val Div   = "b01100_11".BP
  val Divw  = "b01110_11".BP
  val Divu  = "b01100_11".BP
  val Divuw = "b01110_11".BP
  val Rem   = "b01100_11".BP
  val Remw  = "b01110_11".BP
  val Remu  = "b01100_11".BP
  val Remuw = "b01110_11".BP
  // -*- RV64Zicsr/RV32Zicsr: Control and status register (CSR), v
  val Csrrw  = "b11100_11".BP
  val Csrrwi = "b11100_11".BP
  val Csrrs  = "b11100_11".BP
  val Csrrsi = "b11100_11".BP
  val Csrrc  = "b11100_11".BP
  val Csrrci = "b11100_11".BP

  val Unk00 = "b?????_00".BP
  val Unk01 = "b?????_01".BP
  val Unk10 = "b?????_10".BP
}

object AluOpSel extends Enumeration {
  val AluOpFunct3 = Value
  val AluOpAdd    = Value
  val AluOpX      = Value
}

case class InstrPat(
  val funct7:    BitPat,
  val rs2:       BitPat,
  val rs1:       BitPat,
  val funct3:    BitPat,
  val rd:        BitPat,
  val opcode:    BitPat,
  val immFmt:    BitPat,
  val pcSel:     BitPat,
  val srcASel:   BitPat,
  val srcBSel:   BitPat,
  val aluOpSel:  AluOpSel.Value,
  val memAction: BitPat,
  val wbSel:     BitPat,
  val wbEn:      BitPat,
  val excpAdj:   BitPat)
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
    if ((pat.opcode == InstrOpcodeBP.Ebreak) && (pat.rs2 == "b00001".BP)) y
    else n
  }
}

object MemActionField extends DecodeField[InstrPat, UInt] {
  override def name       = "memAction"
  override def chiselType = UInt(MemAction.W)
  override def genTable(pat: InstrPat): BitPat = pat.memAction
}

object MemWidthField extends DecodeField[InstrPat, UInt] {
  override def name       = "memWidth"
  override def chiselType = UInt(MemWidth.W)
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

object AluCalcDirField extends DecodeField[InstrPat, UInt] {
  override def name       = "aluCalcDir"
  override def chiselType = UInt(AluCalcDir.W)
  override def genTable(pat: InstrPat): BitPat = {
    pat.funct7.rawString(1) match {
      case '0' => AluCalcDir.Pos.BP
      case '1' => AluCalcDir.Neg.BP
      case _   => AluCalcDir.Pos.BP
    }
  }
}

object AluBrCondField extends DecodeField[InstrPat, UInt] {
  override def name       = "aluBrCond"
  override def chiselType = UInt(AluBrCond.W)
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

object CsrOpField extends DecodeField[InstrPat, UInt] {
  override def name       = "csrOp"
  override def chiselType = UInt(CsrOp.W)
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

object ImmFmtField extends DecodeField[InstrPat, UInt] {
  override def name       = "immFmt"
  override def chiselType = UInt(ImmFmt.W)
  override def genTable(pat: InstrPat): BitPat = pat.immFmt
}

object PcSelField extends DecodeField[InstrPat, UInt] {
  override def name       = "pcSel"
  override def chiselType = UInt(PcSel.W)
  override def genTable(pat: InstrPat): BitPat = pat.pcSel
}

object SrcASelField extends DecodeField[InstrPat, UInt] {
  override def name       = "srcASel"
  override def chiselType = UInt(ExSrcASel.W)
  override def genTable(pat: InstrPat): BitPat = pat.srcASel
}

object SrcBSelField extends DecodeField[InstrPat, UInt] {
  override def name       = "srcBSel"
  override def chiselType = UInt(ExSrcBSel.W)
  override def genTable(pat: InstrPat): BitPat = pat.srcBSel
}

object WbSelField extends DecodeField[InstrPat, UInt] {
  override def name       = "wbSel"
  override def chiselType = UInt(WbSel.W)
  override def genTable(pat: InstrPat): BitPat = pat.wbSel
}

object WbEnField extends BoolDecodeField[InstrPat] {
  override def name = "wbEn"
  override def genTable(pat: InstrPat): BitPat = pat.wbEn
}

object ExcpAdjField extends DecodeField[InstrPat, UInt] {
  override def name       = "excpAdj"
  override def chiselType = UInt(CsrExcpAdj.W)
  override def genTable(pat: InstrPat): BitPat = pat.excpAdj
}

object InstrInvalField extends BoolDecodeField[InstrPat] {
  override def name = "instrInval"
  override def genTable(pat: InstrPat): BitPat = {
    import InstrOpcodeBP._
    pat.opcode match {
      case Unk00 | Unk01 | Unk10 => 1.Y
      case _                     => 1.N
    }
  }
}

class Idu2ExuMsg extends Bundle {
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
  val inval      = Output(Bool())
  // Pass-through for Exu
  val pc        = Output(UInt(XLen.W))
  val memAction = Output(MemActionField.chiselType)
  val memWidth  = Output(MemWidthField.chiselType)
  val wbEn      = Output(WbEnField.chiselType)
  val wbSel     = Output(WbSelField.chiselType)
  val rdIdx     = Output(UInt(5.W))
  val pcSel     = Output(PcSelField.chiselType)
}

class IduIO extends Bundle {
  val msgIn  = Flipped(Irrevocable(new Ifu2IduMsg))
  val msgOut = Irrevocable(new Idu2ExuMsg)

  val break = Output(Bool())
}

class Idu extends Module {
  import InstrOpcodeBP._
  import AluOpSel._
  import ImmFmt._
  import MemAction._
  import PcSel._
  import ExSrcASel._
  import ExSrcBSel._
  import WbSel._
  import CsrExcpAdj._

  val io = IO(new IduIO())

  private val patterns = Seq(
    // scalafmt: { maxColumn = 512, align.tokens.add = [ { code = "," } ] }
    //      |funct7        |rs2         |rs1 |funct3    |rd  |op     |Fmt        |PcSel      |SrcASel     |SrcBSel    |AluOpSel    |MemAct     |WbSel     |WbEn|ExcpAdj
    InstrPat("b0000000".BP, 5.X,         5.X, "b000".BP, 5.X, Add,    ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b000".BP, 5.X, Addi,   ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat("b0100000".BP, 5.X,         5.X, "b000".BP, 5.X, Sub,    ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, 3.X,       5.X, Lui,    ImmU.BP,    PcSnpc.BP,  SrcAR0.BP,   SrcBImm.BP, AluOpAdd,    MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, 3.X,       5.X, Auipc,  ImmU.BP,    PcSnpc.BP,  SrcAPc.BP,   SrcBImm.BP, AluOpAdd,    MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.N,           5.X,         5.X, "b100".BP, 5.X, Xor,    ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b100".BP, 5.X, Xori,   ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.N,           5.X,         5.X, "b110".BP, 5.X, Or,     ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b110".BP, 5.X, Ori,    ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.N,           5.X,         5.X, "b111".BP, 5.X, And,    ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b111".BP, 5.X, Andi,   ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat("b0000000".BP, 5.X,         5.X, "b001".BP, 5.X, Sll,    ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat("b000000?".BP, 5.X,         5.X, "b001".BP, 5.X, Slli,   ImmIs.BP,   PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat("b0000000".BP, 5.X,         5.X, "b101".BP, 5.X, Srl,    ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat("b000000?".BP, 5.X,         5.X, "b101".BP, 5.X, Srli,   ImmIs.BP,   PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat("b0100000".BP, 5.X,         5.X, "b101".BP, 5.X, Sra,    ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat("b010000?".BP, 5.X,         5.X, "b101".BP, 5.X, Srai,   ImmIs.BP,   PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.N,           5.X,         5.X, "b010".BP, 5.X, Slt,    ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b010".BP, 5.X, Slti,   ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.N,           5.X,         5.X, "b011".BP, 5.X, Sltu,   ImmR.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBRs2.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b011".BP, 5.X, Sltiu,  ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbAlu.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b000".BP, 5.X, Lb,     ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpAdd,    MemRd.BP,   WbMem.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b001".BP, 5.X, Lh,     ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpAdd,    MemRd.BP,   WbMem.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b010".BP, 5.X, Lw,     ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpAdd,    MemRd.BP,   WbMem.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b100".BP, 5.X, Lbu,    ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpAdd,    MemRdu.BP,  WbMem.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b101".BP, 5.X, Lhu,    ImmI.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpAdd,    MemRdu.BP,  WbMem.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b000".BP, 5.X, Sb,     ImmS.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpAdd,    MemWt.BP,   WbSel.X,   1.N, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b001".BP, 5.X, Sh,     ImmS.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpAdd,    MemWt.BP,   WbSel.X,   1.N, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b010".BP, 5.X, Sw,     ImmS.BP,    PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpAdd,    MemWt.BP,   WbSel.X,   1.N, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b000".BP, 5.X, Beq,    ImmB.BP,    PcBr.BP,    SrcARs1.BP,  SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.N, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b001".BP, 5.X, Bne,    ImmB.BP,    PcBr.BP,    SrcARs1.BP,  SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.N, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b100".BP, 5.X, Blt,    ImmB.BP,    PcBr.BP,    SrcARs1.BP,  SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.N, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b101".BP, 5.X, Bge,    ImmB.BP,    PcBr.BP,    SrcARs1.BP,  SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.N, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b110".BP, 5.X, Bltu,   ImmB.BP,    PcBr.BP,    SrcARs1.BP,  SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.N, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b111".BP, 5.X, Bgeu,   ImmB.BP,    PcBr.BP,    SrcARs1.BP,  SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.N, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, 3.X,       5.X, Jal,    ImmJ.BP,    PcAlu.BP,   SrcAPc.BP,   SrcBImm.BP, AluOpFunct3, MemNone.BP, WbSnpc.BP, 1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, 3.N,       5.X, Jalr,   ImmI.BP,    PcAlu.BP,   SrcARs1.BP,  SrcBImm.BP, AluOpFunct3, MemNone.BP, WbSnpc.BP, 1.Y, ExcpAdjNone.BP),
    InstrPat(7.N,           5.N,         5.N, 3.N,       5.N, Ecall,  ImmI.BP,    PcMtvec.BP, SrcAR0.BP,   SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.N, ExcpAdjEcall.BP),
    InstrPat(7.N,           "b00001".BP, 5.N, 3.N,       5.N, Ebreak, ImmI.BP,    PcSnpc.BP,  SrcAR0.BP,   SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.N, ExcpAdjNone.BP),
    InstrPat("b0011000".BP, "b00010".BP, 5.N, 3.N,       5.N, Mret,   ImmR.BP,    PcMepc.BP,  SrcAR0.BP,   SrcBRs2.BP, AluOpX,      MemNone.BP, WbSel.X,   1.N, ExcpAdjMret.BP),
    InstrPat(7.X,           5.X,         5.X, "b001".BP, 5.X, Csrrw,  ImmIcsr.BP, PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpX,      MemNone.BP, WbCsr.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b010".BP, 5.X, Csrrs,  ImmIcsr.BP, PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpX,      MemNone.BP, WbCsr.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b011".BP, 5.X, Csrrc,  ImmIcsr.BP, PcSnpc.BP,  SrcARs1.BP,  SrcBImm.BP, AluOpX,      MemNone.BP, WbCsr.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b101".BP, 5.X, Csrrwi, ImmIcsr.BP, PcSnpc.BP,  SrcAZimm.BP, SrcBImm.BP, AluOpX,      MemNone.BP, WbCsr.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b110".BP, 5.X, Csrrsi, ImmIcsr.BP, PcSnpc.BP,  SrcAZimm.BP, SrcBImm.BP, AluOpX,      MemNone.BP, WbCsr.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, "b111".BP, 5.X, Csrrci, ImmIcsr.BP, PcSnpc.BP,  SrcAZimm.BP, SrcBImm.BP, AluOpX,      MemNone.BP, WbCsr.BP,  1.Y, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, 3.X,       5.X, Unk00,  ImmR.BP,    PcSnpc.BP,  SrcAR0.BP,   SrcBImm.BP, AluOpX,      MemNone.BP, WbSel.X,   1.N, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, 3.X,       5.X, Unk01,  ImmR.BP,    PcSnpc.BP,  SrcAR0.BP,   SrcBImm.BP, AluOpX,      MemNone.BP, WbSel.X,   1.N, ExcpAdjNone.BP),
    InstrPat(7.X,           5.X,         5.X, 3.X,       5.X, Unk10,  ImmR.BP,    PcSnpc.BP,  SrcAR0.BP,   SrcBImm.BP, AluOpX,      MemNone.BP, WbSel.X,   1.N, ExcpAdjNone.BP)
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
    InstrInvalField
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
  io.msgOut.bits.inval      := res(InstrInvalField)

  io.msgOut.bits.pc        := io.msgIn.bits.pc
  io.msgOut.bits.memAction := res(MemActionField)
  io.msgOut.bits.memWidth  := res(MemWidthField)
  io.msgOut.bits.wbEn      := res(WbEnField)
  io.msgOut.bits.wbSel     := res(WbSelField)
  io.msgOut.bits.rdIdx     := io.msgIn.bits.instr(11, 7)
  io.msgOut.bits.pcSel     := res(PcSelField)

  io.break := res(BreakField)

  io.msgIn.ready  := io.msgOut.ready
  io.msgOut.valid := io.msgIn.valid
}
