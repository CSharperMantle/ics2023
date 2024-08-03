package npc

import chisel3._
import chisel3.util._

import common._
import npc._

object AxBurst extends CvtChiselEnum {
  val Fixed    = Value
  val Incr     = Value
  val Wrap     = Value
  val Reserved = Value
}

object AxSize extends CvtChiselEnum {
  val Bytes1   = Value
  val Bytes2   = Value
  val Bytes4   = Value
  val Bytes8   = Value
  val Bytes16  = Value
  val Bytes32  = Value
  val Bytes64  = Value
  val Bytes128 = Value
}

object BResp extends CvtChiselEnum {
  val Okay   = Value
  val ExOkay = Value
  val SlvErr = Value
  val DecErr = Value
}

object RResp extends CvtChiselEnum {
  val Okay   = Value
  val ExOkay = Value
  val SlvErr = Value
  val DecErr = Value
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
