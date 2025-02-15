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

class CsrFileReadConn extends Bundle {
  val valid   = Input(Bool())
  val csrAddr = Input(UInt(12.W))
  val csrVal  = Output(UInt(XLen.W))
  val mepc    = Output(UInt(XLen.W))
  val mtvec   = Output(UInt(XLen.W))
}

class CsrFileWriteConn extends Bundle {
  val valid   = Input(Bool())
  val csrAddr = Input(UInt(12.W))
  val csrWbEn = Input(Bool())
  val csrVal  = Input(UInt(XLen.W))
  val excpAdj = Input(CsrExcpAdj())
  val pc      = Input(UInt(XLen.W))
}

class CsrFile extends Module {
  class Port extends Bundle {
    val read  = new CsrFileReadConn
    val write = new CsrFileWriteConn
  }
  val io = IO(new Port)

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

  private val readPropBundle  = csrPropDecoder.decode(io.read.csrAddr)
  private val writePropBundle = csrPropDecoder.decode(io.write.csrAddr)

  private val readIdx = readPropBundle(CsrPropIdxField)

  private val readVal =
    Mux(readPropBundle(CsrPropIsConstField), readPropBundle(CsrPropConstValField), csrs(readIdx.U))

  io.read.csrVal := readVal
  io.read.mepc   := csrs(MepcIdx.U)
  io.read.mtvec  := csrs(MtvecIdx.U)

  private val mstatus = csrs(MstatusIdx.U)
  // scalafmt: { maxColumn = 512, align.tokens.add = [ { code = "," } ] }
  //                                                | MPP              |               | MPIE      |              | MIE       |
  private val mstatusAdjEcall = Cat(mstatus(31, 13), PrivMode.M.U(2.W), mstatus(10, 8), mstatus(3), mstatus(6, 4), 0.U(1.W),   mstatus(2, 0))
  private val mstatusAdjMret  = Cat(mstatus(31, 13), PrivMode.M.U(2.W), mstatus(10, 8), 1.U(1.W),   mstatus(6, 4), mstatus(7), mstatus(2, 0))
  // scalafmt: { align.tokens.add = [] }

  private val writeIdx = writePropBundle(CsrPropIdxField)
  private val writable = io.write.valid & ~writePropBundle(CsrPropIsConstField)

  for ((csr, idx, hwIdx) <- csrs.zip(BackedCsrIdx.all).map(v => (v._1, v._2.litValue, v._2))) {
    val normalWriteVal = Wire(UInt(XLen.W))
    normalWriteVal := Mux(writable & io.write.csrWbEn, io.write.csrVal, csr)
    if (idx == MstatusIdx.litValue) {
      csr := MuxCase(
        normalWriteVal,
        Seq(
          reset.asBool                        -> InitMstatusVal.U,
          (io.write.excpAdj === ExcpAdjEcall) -> mstatusAdjEcall,
          (io.write.excpAdj === ExcpAdjMret)  -> mstatusAdjMret,
          (~writable | writeIdx =/= hwIdx)    -> csr
        )
      )
    } else if (idx == McauseIdx.litValue) {
      csr := MuxCase(
        normalWriteVal,
        Seq(
          (io.write.excpAdj === ExcpAdjEcall) -> ExcpCode.MEnvCall.U(XLen.W),
          (~writable | writeIdx =/= hwIdx)    -> csr
        )
      )
    } else if (idx == MepcIdx.litValue) {
      csr := MuxCase(
        normalWriteVal,
        Seq(
          (io.write.excpAdj === ExcpAdjEcall) -> io.write.pc,
          (~writable | writeIdx =/= hwIdx)    -> csr
        )
      )
    } else {
      csr := Mux(~writable | writeIdx =/= hwIdx, csr, normalWriteVal)
    }
  }
}
