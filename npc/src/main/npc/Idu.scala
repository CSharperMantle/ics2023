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
}

/** PC update source. */
object InstrPcSel extends CvtChiselEnum {
  val PcSnpc = Value
  val PcAlu  = Value
  val PcBr   = Value
  val PcEpc  = Value
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
  val memAction: BitPat,
  val wbSel:     BitPat,
  val wbEn:      BitPat,
  val aluOvrd:   Boolean)
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

object BreakField extends BoolDecodeField[InstrPat] {
  def name = "break"
  def genTable(pat: InstrPat): BitPat = {
    if ((pat.opcode.equals(InstrOpcodeBP.Ebreak)) && (pat.rs2.equals("b00001".BP))) y else n
  }
}

object MemActionField extends DecodeField[InstrPat, UInt] {
  def name       = "memAction"
  def chiselType = UInt(MemAction.W)
  def genTable(pat: InstrPat): BitPat = pat.memAction
}

object MemWidthField extends DecodeField[InstrPat, UInt] {
  def name       = "memWidth"
  def chiselType = UInt(MemWidth.W)
  def genTable(pat: InstrPat): BitPat = {
    pat.funct3.rawString match {
      case "000" | "100" => MemWidth.LenB.BP
      case "001" | "101" => MemWidth.LenH.BP
      case "010" | "110" => MemWidth.LenW.BP
      case _             => MemWidth.LenD.BP
    }
  }
}

object AluCalcOpField extends DecodeField[InstrPat, UInt] {
  def name       = "aluCalcOp"
  def chiselType = UInt(AluCalcOp.W)
  def genTable(pat: InstrPat): BitPat = {
    if (pat.aluOvrd) AluCalcOp.Add.BP
    else
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
  }
}

object AluCalcDirField extends DecodeField[InstrPat, UInt] {
  def name       = "aluCalcDir"
  def chiselType = UInt(AluCalcDir.W)
  def genTable(pat: InstrPat): BitPat = {
    pat.funct7.rawString(1) match {
      case '0' => AluCalcDir.Pos.BP
      case '1' => AluCalcDir.Neg.BP
      case _   => AluCalcDir.Pos.BP
    }
  }
}

object AluBrCondField extends DecodeField[InstrPat, UInt] {
  def name       = "aluBrCond"
  def chiselType = UInt(AluBrCond.W)
  def genTable(pat: InstrPat): BitPat = {
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
  def name       = "csrOp"
  def chiselType = UInt(CsrOp.W)
  def genTable(pat: InstrPat): BitPat = {
    if (pat.wbSel.equals(WbSel.WbCsr))
      pat.funct3.rawString match {
        case "001" | "101" => CsrOp.Rw.BP
        case "010" | "110" => CsrOp.Rs.BP
        case "011" | "111" => CsrOp.Rc.BP
        case _             => CsrOp.Unk.BP
      }
    else CsrOp.Unk.BP
  }
}

object ImmFmtField extends DecodeField[InstrPat, UInt] {
  def name       = "immFmt"
  def chiselType = UInt(ImmFmt.W)
  def genTable(pat: InstrPat): BitPat = pat.immFmt
}

object PcSelField extends DecodeField[InstrPat, UInt] {
  def name       = "pcSel"
  def chiselType = UInt(InstrPcSel.W)
  def genTable(pat: InstrPat): BitPat = pat.pcSel
}

object SrcASelField extends DecodeField[InstrPat, UInt] {
  def name       = "srcASel"
  def chiselType = UInt(ExSrcASel.W)
  def genTable(pat: InstrPat): BitPat = pat.srcASel
}

object SrcBSelField extends DecodeField[InstrPat, UInt] {
  def name       = "srcBSel"
  def chiselType = UInt(ExSrcBSel.W)
  def genTable(pat: InstrPat): BitPat = pat.srcBSel
}

object WbSelField extends DecodeField[InstrPat, UInt] {
  def name       = "wbSel"
  def chiselType = UInt(WbSel.W)
  def genTable(pat: InstrPat): BitPat = pat.wbSel
}

object WbEnField extends BoolDecodeField[InstrPat] {
  def name = "wbEn"
  def genTable(pat: InstrPat): BitPat = pat.wbEn
}

class IduIO extends Bundle {
  val instr      = Input(UInt(32.W))
  val break      = Output(Bool())
  val memWidth   = Output(MemWidthField.chiselType)
  val rs1Idx     = Output(UInt(5.W))
  val rs2Idx     = Output(UInt(5.W))
  val rdIdx      = Output(UInt(5.W))
  val aluCalcOp  = Output(AluCalcOpField.chiselType)
  val aluCalcDir = Output(AluCalcDirField.chiselType)
  val aluBrCond  = Output(AluBrCondField.chiselType)
  val csrOp      = Output(CsrOpField.chiselType)
  val imm        = Output(UInt(XLen.W))
  val pcSel      = Output(PcSelField.chiselType)
  val srcASel    = Output(SrcASelField.chiselType)
  val srcBSel    = Output(SrcBSelField.chiselType)
  val memAction  = Output(MemActionField.chiselType)
  val wbSel      = Output(WbSelField.chiselType)
  val wbEn       = Output(WbEnField.chiselType)
}

class Idu extends Module {
  import InstrOpcodeBP._
  import InstrPcSel._
  import ExSrcASel._
  import ExSrcBSel._
  import WbSel._
  import ImmFmt._
  import MemAction._

  val io = IO(new IduIO())

  val patterns = Seq(
    // scalafmt: { align.tokens.add = [ { code = "," } ] }
    //      |funct7        |rs2         |rs1 |funct3    |rd  |op     |Fmt        |PcSel    |SrcASel      |SrcBSel    |MemAct  |WbSel     |WbEn|AluOvrd
    InstrPat("b0000000".BP, 5.X,         5.X, "b000".BP, 5.X, Add,    ImmR.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBRs2.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat(7.X,           5.X,         5.X, "b000".BP, 5.X, Addi,   ImmI.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat("b0100000".BP, 5.X,         5.X, "b000".BP, 5.X, Sub,    ImmR.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBRs2.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat(7.X,           5.X,         5.X, 3.X,       5.X, Lui,    ImmU.BP,    PcSnpc.BP, SrcAR0.BP,   SrcBImm.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat(7.X,           5.X,         5.X, 3.X,       5.X, Auipc,  ImmU.BP,    PcSnpc.BP, SrcAPc.BP,   SrcBImm.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat(7.N,           5.X,         5.X, "b100".BP, 5.X, Xor,    ImmR.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBRs2.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat(7.X,           5.X,         5.X, "b100".BP, 5.X, Xori,   ImmI.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat(7.N,           5.X,         5.X, "b110".BP, 5.X, Or,     ImmR.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBRs2.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat(7.X,           5.X,         5.X, "b110".BP, 5.X, Ori,    ImmI.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat(7.N,           5.X,         5.X, "b111".BP, 5.X, And,    ImmR.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBRs2.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat(7.X,           5.X,         5.X, "b111".BP, 5.X, Andi,   ImmI.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat("b0000000".BP, 5.X,         5.X, "b001".BP, 5.X, Sll,    ImmR.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBRs2.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat("b000000?".BP, 5.X,         5.X, "b001".BP, 5.X, Slli,   ImmIs.BP,   PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat("b0000000".BP, 5.X,         5.X, "b101".BP, 5.X, Srl,    ImmR.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBRs2.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat("b000000?".BP, 5.X,         5.X, "b101".BP, 5.X, Srli,   ImmIs.BP,   PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat("b0100000".BP, 5.X,         5.X, "b101".BP, 5.X, Sra,    ImmR.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBRs2.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat("b010000?".BP, 5.X,         5.X, "b101".BP, 5.X, Srai,   ImmIs.BP,   PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat(7.N,           5.X,         5.X, "b010".BP, 5.X, Slt,    ImmR.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBRs2.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat(7.X,           5.X,         5.X, "b010".BP, 5.X, Slti,   ImmI.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat(7.N,           5.X,         5.X, "b011".BP, 5.X, Sltu,   ImmR.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBRs2.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat(7.X,           5.X,         5.X, "b011".BP, 5.X, Sltiu,  ImmI.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, None.BP, WbAlu.BP,  1.Y, false),
    InstrPat(7.X,           5.X,         5.X, "b000".BP, 5.X, Lb,     ImmI.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, Rd.BP,   WbMem.BP,  1.Y, true),
    InstrPat(7.X,           5.X,         5.X, "b001".BP, 5.X, Lh,     ImmI.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, Rd.BP,   WbMem.BP,  1.Y, true),
    InstrPat(7.X,           5.X,         5.X, "b010".BP, 5.X, Lw,     ImmI.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, Rd.BP,   WbMem.BP,  1.Y, true),
    InstrPat(7.X,           5.X,         5.X, "b100".BP, 5.X, Lbu,    ImmI.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, Rdu.BP,  WbMem.BP,  1.Y, true),
    InstrPat(7.X,           5.X,         5.X, "b101".BP, 5.X, Lhu,    ImmI.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, Rdu.BP,  WbMem.BP,  1.Y, true),
    InstrPat(7.X,           5.X,         5.X, "b000".BP, 5.X, Sb,     ImmS.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, Wt.BP,   WbAlu.BP,  1.N, true),
    InstrPat(7.X,           5.X,         5.X, "b001".BP, 5.X, Sh,     ImmS.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, Wt.BP,   WbAlu.BP,  1.N, true),
    InstrPat(7.X,           5.X,         5.X, "b010".BP, 5.X, Sw,     ImmS.BP,    PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, Wt.BP,   WbAlu.BP,  1.N, true),
    InstrPat(7.X,           5.X,         5.X, "b000".BP, 5.X, Beq,    ImmB.BP,    PcBr.BP,   SrcARs1.BP,  SrcBRs2.BP, None.BP, WbAlu.BP,  1.N, true),
    InstrPat(7.X,           5.X,         5.X, "b001".BP, 5.X, Bne,    ImmB.BP,    PcBr.BP,   SrcARs1.BP,  SrcBRs2.BP, None.BP, WbAlu.BP,  1.N, true),
    InstrPat(7.X,           5.X,         5.X, "b100".BP, 5.X, Blt,    ImmB.BP,    PcBr.BP,   SrcARs1.BP,  SrcBRs2.BP, None.BP, WbAlu.BP,  1.N, true),
    InstrPat(7.X,           5.X,         5.X, "b101".BP, 5.X, Bge,    ImmB.BP,    PcBr.BP,   SrcARs1.BP,  SrcBRs2.BP, None.BP, WbAlu.BP,  1.N, true),
    InstrPat(7.X,           5.X,         5.X, "b110".BP, 5.X, Bltu,   ImmB.BP,    PcBr.BP,   SrcARs1.BP,  SrcBRs2.BP, None.BP, WbAlu.BP,  1.N, true),
    InstrPat(7.X,           5.X,         5.X, "b111".BP, 5.X, Bgeu,   ImmB.BP,    PcBr.BP,   SrcARs1.BP,  SrcBRs2.BP, None.BP, WbAlu.BP,  1.N, true),
    InstrPat(7.X,           5.X,         5.X, 3.X,       5.X, Jal,    ImmJ.BP,    PcAlu.BP,  SrcAPc.BP,   SrcBImm.BP, None.BP, WbSnpc.BP, 1.Y, false),
    InstrPat(7.X,           5.X,         5.X, 3.N,       5.X, Jalr,   ImmI.BP,    PcAlu.BP,  SrcARs1.BP,  SrcBImm.BP, None.BP, WbSnpc.BP, 1.Y, false),
    InstrPat(7.N,           5.N,         5.N, 3.N,       5.N, Ecall,  ImmI.BP,    PcSnpc.BP, SrcAR0.BP,   SrcBRs2.BP, None.BP, WbAlu.BP,  1.N, false),
    InstrPat(7.N,           "b00001".BP, 5.N, 3.N,       5.N, Ebreak, ImmI.BP,    PcSnpc.BP, SrcAR0.BP,   SrcBRs2.BP, None.BP, WbAlu.BP,  1.N, false),
    InstrPat("b0011000".BP, "b00010".BP, 5.N, 3.N,       5.N, Mret,   ImmR.BP,    PcEpc.BP,  SrcAR0.BP,   SrcBRs2.BP, None.BP, WbAlu.BP,  1.N, false),
    InstrPat(7.X,           5.X,         5.X, "b001".BP, 5.X, Csrrw,  ImmIcsr.BP, PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, None.BP, WbCsr.BP,  1.Y, false),
    InstrPat(7.X,           5.X,         5.X, "b010".BP, 5.X, Csrrs,  ImmIcsr.BP, PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, None.BP, WbCsr.BP,  1.Y, false),
    InstrPat(7.X,           5.X,         5.X, "b011".BP, 5.X, Csrrc,  ImmIcsr.BP, PcSnpc.BP, SrcARs1.BP,  SrcBImm.BP, None.BP, WbCsr.BP,  1.Y, false),
    InstrPat(7.X,           5.X,         5.X, "b101".BP, 5.X, Csrrwi, ImmIcsr.BP, PcSnpc.BP, SrcAZimm.BP, SrcBImm.BP, None.BP, WbCsr.BP,  1.Y, false),
    InstrPat(7.X,           5.X,         5.X, "b110".BP, 5.X, Csrrsi, ImmIcsr.BP, PcSnpc.BP, SrcAZimm.BP, SrcBImm.BP, None.BP, WbCsr.BP,  1.Y, false),
    InstrPat(7.X,           5.X,         5.X, "b111".BP, 5.X, Csrrci, ImmIcsr.BP, PcSnpc.BP, SrcAZimm.BP, SrcBImm.BP, None.BP, WbCsr.BP,  1.Y, false)
  )
  val fields = Seq(
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
    WbEnField
  )
  val table = new DecodeTable(patterns, fields)
  val res   = table.decode(io.instr)

  io.break      := res(BreakField)
  io.memWidth   := res(MemWidthField)
  io.aluCalcOp  := res(AluCalcOpField)
  io.aluCalcDir := res(AluCalcDirField)
  io.aluBrCond  := res(AluBrCondField)
  io.csrOp      := res(CsrOpField)
  io.pcSel      := res(PcSelField)
  io.srcASel    := res(SrcASelField)
  io.srcBSel    := res(SrcBSelField)
  io.memAction  := res(MemActionField)
  io.wbSel      := res(WbSelField)
  io.wbEn       := res(WbEnField)

  val immDec = Module(new ImmDec)
  immDec.io.instr  := io.instr
  immDec.io.immFmt := res(ImmFmtField)
  io.imm           := immDec.io.imm

  io.rs1Idx := io.instr(24, 20)
  io.rs2Idx := io.instr(19, 15)
  io.rdIdx  := io.instr(11, 7)
}
