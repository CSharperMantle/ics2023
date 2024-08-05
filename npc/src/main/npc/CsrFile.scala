package npc

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import common._
import npc._

object KnownCsrIdx extends CvtChiselEnum {
  val SatpIdx      = Value
  val MstatusIdx   = Value
  val MieIdx       = Value
  val MtvecIdx     = Value
  val MscratchIdx  = Value
  val MepcIdx      = Value
  val McauseIdx    = Value
  val MtvalIdx     = Value
  val MipIdx       = Value
  val MvendoridIdx = Value
  val MarchidIdx   = Value
  val MimpidIdx    = Value
}

case class CsrPropPattern(
  val addr:     BitPat,
  val idx:      BitPat,
  val constVal: Option[UInt])
    extends DecodePattern {
  override def bitPat = addr
}

object CsrPropIdxField extends DecodeField[CsrPropPattern, UInt] {
  override def name = "idx"
  override def chiselType = UInt(KnownCsrIdx.W)
  override def genTable(op: CsrPropPattern): BitPat = op.idx
}

object CsrPropIsConstField extends BoolDecodeField[CsrPropPattern] {
  override def name = "isConst"
  override def genTable(op: CsrPropPattern): BitPat = {
    if (op.constVal.isDefined) y else n
  }
}

object CsrPropConstValField extends DecodeField[CsrPropPattern, UInt] {
  override def name = "constVal"
  override def chiselType = UInt(XLen.W)
  override def genTable(op: CsrPropPattern): BitPat = {
    op.constVal match {
      case Some(v) => BitPat(v)
      case None    => BitPat.dontCare(XLen)
    }
  }
}

object CsrExcpAdj extends CvtChiselEnum {
  val ExcpAdjNone  = Value
  val ExcpAdjEcall = Value
  val ExcpAdjMret  = Value
}

object CsrOp extends CvtChiselEnum {
  val Rw  = Value
  val Rs  = Value
  val Rc  = Value
  val Unk = Value
}

class CsrFileConn extends Bundle {
  val csrAddr = Input(UInt(12.W))
  val csrOp   = Input(UInt(CsrOp.W))
  val s1      = Input(UInt(XLen.W))
  val excpAdj = Input(UInt(CsrExcpAdj.W))
  val pc      = Input(UInt(XLen.W))
  val csrVal  = Output(UInt(XLen.W))
  val mepc    = Output(UInt(XLen.W))
  val mtvec   = Output(UInt(XLen.W))
}

class CsrFileIO extends Bundle {
  val conn = new CsrFileConn
}

class CsrFile extends Module {
  import CsrOp._
  import CsrExcpAdj._
  import KnownCsrIdx._

  val io = IO(new CsrFileIO)

  private val csrs = Mem(KnownCsrIdx.all.length, UInt(XLen.W))

  private val csrOpDec = Decoder1H(
    Seq(
      Rw.BP -> 0,
      Rs.BP -> 1,
      Rc.BP -> 2
    )
  )
  private val csrOp1H = csrOpDec(io.conn.csrOp)

  private val csrPropTable = Seq(
    // scalafmt: { maxColumn = 512, align.tokens.add = [ { code = "," } ] }
    CsrPropPattern(BitPat("h180".U(12.W)), SatpIdx.BP,      None),
    CsrPropPattern(BitPat("h300".U(12.W)), MstatusIdx.BP,   None),
    CsrPropPattern(BitPat("h304".U(12.W)), MieIdx.BP,       None),
    CsrPropPattern(BitPat("h305".U(12.W)), MtvecIdx.BP,     None),
    CsrPropPattern(BitPat("h340".U(12.W)), MscratchIdx.BP,  None),
    CsrPropPattern(BitPat("h341".U(12.W)), MepcIdx.BP,      None),
    CsrPropPattern(BitPat("h342".U(12.W)), McauseIdx.BP,    None),
    CsrPropPattern(BitPat("h343".U(12.W)), MtvalIdx.BP,     None),
    CsrPropPattern(BitPat("h344".U(12.W)), MipIdx.BP,       None),
    CsrPropPattern(BitPat("hf11".U(12.W)), MvendoridIdx.BP, Some("h79737978".U(XLen.W))),
    CsrPropPattern(BitPat("hf12".U(12.W)), MarchidIdx.BP,   Some("h015fdf40".U(XLen.W))),
    CsrPropPattern(BitPat("hf13".U(12.W)), MimpidIdx.BP,    Some("h00000001".U(XLen.W)))
    // scalafmt: { align.tokens.add = [] }
  )
  private val csrPropFields = Seq(
    CsrPropIdxField,
    CsrPropIsConstField,
    CsrPropConstValField
  )
  private val csrPropDecoder = new DecodeTable(csrPropTable, csrPropFields)
  private val csrPropBundle  = csrPropDecoder.decode(io.conn.csrAddr)

  private val regIdx = csrPropBundle(CsrPropIdxField)

  private val csrIsConst = csrPropBundle(CsrPropIsConstField)

  private val csrVal = Mux(csrIsConst, csrPropBundle(CsrPropConstValField), csrs(regIdx))
  io.conn.csrVal := csrVal
  csrs(regIdx) := Mux(
    csrIsConst,
    csrVal,
    Mux1H(
      Seq(
        csrOp1H(0) -> io.conn.s1,
        csrOp1H(1) -> (io.conn.s1 | csrVal),
        csrOp1H(2) -> (~io.conn.s1 & csrVal),
        csrOp1H(3) -> csrVal
      )
    )
  )

  private val excpAdjDec = Decoder1H(
    Seq(
      ExcpAdjNone.BP  -> 0,
      ExcpAdjEcall.BP -> 1,
      ExcpAdjMret.BP  -> 2
    )
  )
  private val excpAdj1H = excpAdjDec(io.conn.excpAdj)

  private val mstatus = csrs(MstatusIdx.U)

  csrs(McauseIdx.U) := Mux(excpAdj1H(1), ExcpCode.MEnvCall.U(XLen.W), csrs(McauseIdx.U))
  csrs(MepcIdx.U)   := Mux(excpAdj1H(1), io.conn.pc, csrs(MepcIdx.U))

  // scalafmt: { maxColumn = 512, align.tokens.add = [ { code = "," } ] }
  //                                                | MPP              |               | MPIE      |              | MIE       |
  private val mstatusAdjMret  = Cat(mstatus(31, 13), PrivMode.M.U(2.W), mstatus(10, 8), 1.U(1.W),   mstatus(6, 4), mstatus(7), mstatus(2, 0))
  private val mstatusAdjEcall = Cat(mstatus(31, 13), PrivMode.M.U(2.W), mstatus(10, 8), mstatus(3), mstatus(6, 4), 0.U(1.W),   mstatus(2, 0))
  // scalafmt: { align.tokens.add = [] }
  csrs(MstatusIdx.U) := Mux1H(
    Seq(
      excpAdj1H(0) -> mstatus,
      excpAdj1H(1) -> mstatusAdjEcall,
      excpAdj1H(2) -> mstatusAdjMret,
      excpAdj1H(3) -> mstatus
    )
  )

  io.conn.mepc  := csrs(MepcIdx.U)
  io.conn.mtvec := csrs(MtvecIdx.U)
}
