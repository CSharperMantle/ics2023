package npc

import chisel3._
import chisel3.util._

import common._
import npc._

object MemWidth extends CvtChiselEnum {
  val LenB = Value
  val LenH = Value
  val LenW = Value
  val LenD = Value
}

object MemAction extends CvtChiselEnum {
  val Rd   = Value
  val Rdu  = Value
  val Wt   = Value
  val None = Value
}

class MemuBlackBoxIO extends Bundle {
  val clock    = Input(Clock())
  val memWidth = Input(UInt(MemWidth.W))
  val memU     = Input(Bool())
  val memREn   = Input(Bool())
  val memRAddr = Input(UInt(XLen.W))
  val memRData = Output(UInt(XLen.W))
  val memWEn   = Input(Bool())
  val memWAddr = Input(UInt(XLen.W))
  val memWData = Input(UInt(XLen.W))
}

class MemuBlackBox extends BlackBox with HasBlackBoxInline {
  val io = IO(new MemuBlackBoxIO)

  val xLenType  = if (XLen == 32) "int" else "longint"
  val memWidthW = MemWidth.getWidth

  setInline(
    "MemuBlackBox.sv",
    s"""
       |import "DPI-C" function void npc_dpi_memu(input  byte      mem_width,
       |                                          input  bit       mem_u,
       |                                          input  bit       mem_r_en,
       |                                          input  $xLenType mem_r_addr,
       |                                          output $xLenType mem_r_data,
       |                                          input  bit       mem_w_en,
       |                                          input  $xLenType mem_w_addr,
       |                                          input  $xLenType mem_w_data);
       |
       |module MemuBlackBox(
       |  input                       clock,
       |  input  [${memWidthW - 1}:0] memWidth,
       |  input                       memU,
       |  input                       memREn,
       |  input  [${XLen - 1}:0]      memRAddr,
       |  output [${XLen - 1}:0]      memRData,
       |  input                       memWEn,
       |  input  [${XLen - 1}:0]      memWAddr,
       |  input  [${XLen - 1}:0]      memWData
       |);
       |  wire [7:0] memWidthExt;
       |  assign memWidthExt = {${8 - memWidthW}'b0, memWidth};
       |  always @(posedge clock) begin
       |    npc_dpi_memu(memWidthExt, memU, memREn, memRAddr, memRData, memWEn, memWAddr, memWData);
       |  end
       |endmodule
       |""".stripMargin
  )
}

class MemuIO extends Bundle {
  val memWidth  = Input(UInt(MemWidth.W))
  val memAction = Input(UInt(MemAction.W))
  val memRAddr  = Input(UInt(XLen.W))
  val memRData  = Output(UInt(XLen.W))
  val memWAddr  = Input(UInt(XLen.W))
  val memWData  = Input(UInt(XLen.W))
  val inval = Output(Bool())
}

class Memu extends Module {
  val io = IO(new MemuIO)

  val memActionDec = Decoder1H(
    Seq(
      MemAction.Rd.BP   -> 0,
      MemAction.Rdu.BP  -> 1,
      MemAction.Wt.BP   -> 2,
      MemAction.None.BP -> 3
    )
  )
  val memAction1H = memActionDec(io.memAction)

  val backend = Module(new MemuBlackBox)
  backend.io.clock    := clock
  backend.io.memWidth := io.memWidth
  backend.io.memU     := memAction1H(1)
  backend.io.memREn   := memAction1H(0) | memAction1H(1)
  backend.io.memRAddr := io.memRAddr
  backend.io.memWAddr := io.memWAddr
  backend.io.memWData := io.memWData
  backend.io.memWEn   := memAction1H(2)

  val sext = Module(new SExtender)
  sext.io.sextData := backend.io.memRData
  sext.io.sextW    := io.memWidth
  io.memRData      := sext.io.sextRes

  io.inval := memAction1H(memActionDec.bitBad)
}
