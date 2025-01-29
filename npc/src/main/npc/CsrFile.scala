package npc

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import common._
import npc._

object BackedCsrIdx extends CvtChiselEnum {
  val MstatusIdx  = Value
  val MieIdx      = Value
  val MtvecIdx    = Value
  val MscratchIdx = Value
  val MepcIdx     = Value
  val McauseIdx   = Value
  val MtvalIdx    = Value
  val MipIdx      = Value
}

case class CsrPropPattern(
  val addr:     BitPat,
  val idx:      BitPat,
  val constVal: Option[UInt])
    extends DecodePattern {
  override def bitPat = addr
}

object CsrPropIdxField extends DecodeField[CsrPropPattern, BackedCsrIdx.Type] {
  override def name       = "idx"
  override def chiselType = BackedCsrIdx()
  override def genTable(op: CsrPropPattern): BitPat = op.idx
}

object CsrPropIsConstField extends BoolDecodeField[CsrPropPattern] {
  override def name = "isConst"
  override def genTable(op: CsrPropPattern): BitPat = {
    if (op.constVal.isDefined) y else n
  }
}

object CsrPropConstValField extends DecodeField[CsrPropPattern, UInt] {
  override def name       = "constVal"
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
  val valid   = Input(Bool())
  val csrAddr = Input(UInt(12.W))
  val csrOp   = Input(CsrOp())
  val s1      = Input(UInt(XLen.W))
  val excpAdj = Input(CsrExcpAdj())
  val pc      = Input(UInt(XLen.W))
  val csrVal  = Output(UInt(XLen.W))
  val mepc    = Output(UInt(XLen.W))
  val mtvec   = Output(UInt(XLen.W))
  val ready   = Output(Bool())
}

class CsrFile extends Module {
  class Port extends Bundle {
    val conn = new CsrFileConn
  }
  val io = IO(new Port)

  import CsrOp._
  import CsrExcpAdj._
  import BackedCsrIdx._

  private val csrs = RegInit(VecInit(Seq.fill(BackedCsrIdx.all.length)(0.U(XLen.W))))

  private val csrPropTable = Seq(
    // scalafmt: { maxColumn = 512, align.tokens.add = [ { code = "," } ] }
    CsrPropPattern(BitPat("h300".U(12.W)), MstatusIdx.BP,  None),
    CsrPropPattern(BitPat("h304".U(12.W)), MieIdx.BP,      None),
    CsrPropPattern(BitPat("h305".U(12.W)), MtvecIdx.BP,    None),
    CsrPropPattern(BitPat("h340".U(12.W)), MscratchIdx.BP, None),
    CsrPropPattern(BitPat("h341".U(12.W)), MepcIdx.BP,     None),
    CsrPropPattern(BitPat("h342".U(12.W)), McauseIdx.BP,   None),
    CsrPropPattern(BitPat("h343".U(12.W)), MtvalIdx.BP,    None),
    CsrPropPattern(BitPat("h344".U(12.W)), MipIdx.BP,      None),
    CsrPropPattern(BitPat("hf11".U(12.W)), BackedCsrIdx.X, Some("h79737978".U(XLen.W))), // mvendorid
    CsrPropPattern(BitPat("hf12".U(12.W)), BackedCsrIdx.X, Some("h015fdf40".U(XLen.W))), // marchid
    CsrPropPattern(BitPat("hf13".U(12.W)), BackedCsrIdx.X, Some("h00000001".U(XLen.W))) // mimpid
    // scalafmt: { align.tokens.add = [] }
  )
  private val csrPropFields = Seq(
    CsrPropIdxField,
    CsrPropIsConstField,
    CsrPropConstValField
  )
  private val csrPropDecoder = new DecodeTable(csrPropTable, csrPropFields)
  private val csrPropBundle  = csrPropDecoder.decode(io.conn.csrAddr)

  private val csrIdx     = csrPropBundle(CsrPropIdxField)
  private val csrIsConst = csrPropBundle(CsrPropIsConstField)

  private val csrVal = Wire(UInt(XLen.W))
  csrVal := Mux(csrIsConst, csrPropBundle(CsrPropConstValField), csrs(csrIdx.U))

  io.conn.csrVal := csrVal
  io.conn.mepc   := csrs(MepcIdx.U)
  io.conn.mtvec  := csrs(MtvecIdx.U)

  private val mstatus = csrs(MstatusIdx.U)
  // scalafmt: { maxColumn = 512, align.tokens.add = [ { code = "," } ] }
  //                                                | MPP              |               | MPIE      |              | MIE       |
  private val mstatusAdjEcall = Cat(mstatus(31, 13), PrivMode.M.U(2.W), mstatus(10, 8), mstatus(3), mstatus(6, 4), 0.U(1.W),   mstatus(2, 0))
  private val mstatusAdjMret  = Cat(mstatus(31, 13), PrivMode.M.U(2.W), mstatus(10, 8), 1.U(1.W),   mstatus(6, 4), mstatus(7), mstatus(2, 0))
  // scalafmt: { align.tokens.add = [] }

  private object State extends CvtChiselEnum {
    val S_Idle    = Value
    val S_ExcpAdj = Value
    val S_Write   = Value
    val S_Done    = Value
  }
  import State._
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle -> Mux(
        ~io.conn.valid,
        S_Idle,
        MuxCase(
          S_Done,
          Seq(
            (io.conn.excpAdj =/= ExcpAdjNone) -> S_ExcpAdj,
            (io.conn.csrOp =/= Unk)           -> S_Write
          )
        )
      ),
      S_ExcpAdj -> S_Done,
      S_Write   -> S_Done,
      S_Done    -> S_Idle
    )
  )

  private val writable = y === S_Write & ~csrIsConst

  for ((csr, idx, hwIdx) <- csrs.zip(BackedCsrIdx.all).map(v => (v._1, v._2.litValue, v._2))) {
    val normalWriteVal = Wire(UInt(XLen.W))
    normalWriteVal := MuxLookup(io.conn.csrOp, csr)(
      Seq(
        Rw -> io.conn.s1,
        Rs -> (io.conn.s1 | csr),
        Rc -> (~io.conn.s1 & csr)
      )
    )
    if (idx == MstatusIdx.litValue) {
      csr := MuxCase(
        normalWriteVal,
        Seq(
          reset.asBool                                         -> InitMstatusVal.U,
          (y === S_ExcpAdj & io.conn.excpAdj === ExcpAdjEcall) -> mstatusAdjEcall,
          (y === S_ExcpAdj & io.conn.excpAdj === ExcpAdjMret)  -> mstatusAdjMret,
          (~writable | csrIdx =/= hwIdx)                       -> csr
        )
      )
    } else if (idx == McauseIdx.litValue) {
      csr := MuxCase(
        normalWriteVal,
        Seq(
          (y === S_ExcpAdj & io.conn.excpAdj === ExcpAdjEcall) -> ExcpCode.MEnvCall.U(XLen.W),
          (~writable | csrIdx =/= hwIdx)                       -> csr
        )
      )
    } else if (idx == MepcIdx.litValue) {
      csr := MuxCase(
        normalWriteVal,
        Seq(
          (y === S_ExcpAdj & io.conn.excpAdj === ExcpAdjEcall) -> io.conn.pc,
          (~writable | csrIdx =/= hwIdx)                       -> csr
        )
      )
    } else {
      csr := Mux(~writable | csrIdx =/= hwIdx, csr, normalWriteVal)
    }
  }

  io.conn.ready := y === S_Done
}
