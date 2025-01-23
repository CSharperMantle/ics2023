package npc

import chisel3._
import chisel3.util._

import common._
import npc._

object AxBurst extends CvtChiselEnum {
  val Fixed    = Value(0x0.U)
  val Incr     = Value(0x1.U)
  val Wrap     = Value(0x2.U)
  val Reserved = Value(0x3.U)
}

object AxSize extends CvtChiselEnum {
  val Bytes1   = Value(0x0.U)
  val Bytes2   = Value(0x1.U)
  val Bytes4   = Value(0x2.U)
  val Bytes8   = Value(0x3.U)
  val Bytes16  = Value(0x4.U)
  val Bytes32  = Value(0x5.U)
  val Bytes64  = Value(0x6.U)
  val Bytes128 = Value(0x7.U)
}

object BResp extends CvtChiselEnum {
  val Okay   = Value(0x0.U)
  val ExOkay = Value(0x1.U)
  val SlvErr = Value(0x2.U)
  val DecErr = Value(0x3.U)
}

object RResp extends CvtChiselEnum {
  val Okay   = Value(0x0.U)
  val ExOkay = Value(0x1.U)
  val SlvErr = Value(0x2.U)
  val DecErr = Value(0x3.U)
}

class Axi4MasterPort extends Bundle {
  val awready = Input(Bool())
  val awvalid = Output(Bool())
  val awaddr  = Output(UInt(32.W))
  val awid    = Output(UInt(4.W))
  val awlen   = Output(UInt(8.W))
  val awsize  = Output(UInt(3.W))
  val awburst = Output(UInt(2.W))

  val wready = Input(Bool())
  val wvalid = Output(Bool())
  val wdata  = Output(UInt(32.W))
  val wstrb  = Output(UInt(4.W))
  val wlast  = Output(Bool())
  val bready = Output(Bool())
  val bvalid = Input(Bool())
  val bresp  = Input(UInt(2.W))
  val bid    = Input(UInt(4.W))

  val arready = Input(Bool())
  val arvalid = Output(Bool())
  val araddr  = Output(UInt(32.W))
  val arid    = Output(UInt(4.W))
  val arlen   = Output(UInt(8.W))
  val arsize  = Output(UInt(3.W))
  val arburst = Output(UInt(2.W))

  val rready = Output(Bool())
  val rvalid = Input(Bool())
  val rresp  = Input(UInt(2.W))
  val rdata  = Input(UInt(32.W))
  val rlast  = Input(Bool())
  val rid    = Input(UInt(4.W))
}
