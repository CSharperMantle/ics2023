package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class SramRPortBlackBoxIO(addrWidth: Width, dataWidth: Width) extends Bundle {
  val rEn   = Input(Bool())
  val rAddr = Input(UInt(addrWidth))
  val rData = Output(UInt(dataWidth))
}

class SramRPortBlackBox(addrWidth: Width, dataWidth: Width)
    extends BlackBox
    with HasBlackBoxInline {
  require(addrWidth.isInstanceOf[KnownWidth] && dataWidth.isInstanceOf[KnownWidth])

  val io = IO(new SramRPortBlackBoxIO(addrWidth, dataWidth))

  val addrType     = getDpiType(addrWidth)
  val addrWidthVal = addrWidth.asInstanceOf[KnownWidth].value
  val dataType     = getDpiType(dataWidth)
  val dataWidthVal = dataWidth.asInstanceOf[KnownWidth].value
  setInline(
    "SramRPortBlackBox.sv",
    s"""
       |module SramRPortBlackBox(
       |  input                              rEn,
       |  input      [${addrWidthVal - 1}:0] rAddr,
       |  output reg [${dataWidthVal - 1}:0] rData
       |);
       |  import "DPI-C" function $dataType npc_dpi_pmem_read(input $addrType mem_r_addr);
       |
       |  always @(*) begin
       |    if (rEn) begin
       |      rData = npc_dpi_pmem_read(rAddr);
       |    end else begin
       |      rData = 32'hdead3210;
       |    end
       |  end
       |endmodule
       |""".stripMargin
  )
}

class SramRPortIO(addrWidth: Width, dataWidth: Width) extends Bundle {
  val rEn   = Input(Bool())
  val rAddr = Input(UInt(addrWidth))
  val rData = Output(UInt(dataWidth))
  val done  = Output(Bool())
}

class SramRPort(addrWidth: Width, dataWidth: Width) extends Module {
  val io = IO(new SramRPortIO(addrWidth, dataWidth))

  val backend = Module(new SramRPortBlackBox(addrWidth, dataWidth))

  object State extends CvtChiselEnum {
    val S_Idle = Value
    val S_Read = Value
  }
  import State._
  val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle -> Mux(io.rEn, S_Read, S_Idle),
      S_Read -> S_Idle
    )
  )

  val data = RegEnable(backend.io.rData, y === S_Read)

  backend.io.rEn   := y === S_Read
  backend.io.rAddr := io.rAddr

  io.rData := data
  io.done  := y === S_Read
}

class SramWPortBlackBoxIO(addrWidth: Width, dataWidth: Width) extends Bundle {
  val wEn   = Input(Bool())
  val wAddr = Input(UInt(addrWidth))
  val wData = Input(UInt(dataWidth))
  val wMask = Input(UInt(8.W))
}

class SramWPortBlackBox(addrWidth: Width, dataWidth: Width)
    extends BlackBox
    with HasBlackBoxInline {
  require(addrWidth.isInstanceOf[KnownWidth] && dataWidth.isInstanceOf[KnownWidth])

  val io = IO(new SramWPortBlackBoxIO(addrWidth, dataWidth))

  val addrType     = getDpiType(addrWidth)
  val addrWidthVal = addrWidth.asInstanceOf[KnownWidth].value
  val dataType     = getDpiType(dataWidth)
  val dataWidthVal = dataWidth.asInstanceOf[KnownWidth].value
  setInline(
    "SramWPortBlackBox.sv",
    s"""
       |module SramWPortBlackBox(
       |  input                         wEn,
       |  input [${addrWidthVal - 1}:0] wAddr,
       |  input [${dataWidthVal - 1}:0] wData,
       |  input [7:0]                   wMask
       |);
       |  import "DPI-C" function void npc_dpi_pmem_write(input byte      mem_mask,
       |                                                  input $addrType mem_w_addr,
       |                                                  input $dataType mem_w_data);
       |
       |  always @(*) begin
       |    if (wEn) begin
       |      npc_dpi_pmem_write(wMask, wAddr, wData);
       |    end
       |  end
       |endmodule
       |""".stripMargin
  )
}

class SramWPortIO(addrWidth: Width, dataWidth: Width) extends Bundle {
  val wEn   = Input(Bool())
  val wAddr = Input(UInt(addrWidth))
  val wData = Input(UInt(dataWidth))
  val wMask = Input(UInt(8.W))
  val done  = Output(Bool())
}

class SramWPort(addrWidth: Width, dataWidth: Width) extends Module {
  val io = IO(new SramWPortIO(addrWidth, dataWidth))

  val backend = Module(new SramWPortBlackBox(addrWidth, dataWidth))

  object State extends CvtChiselEnum {
    val S_Idle  = Value
    val S_Write = Value
    val S_Delay = Value
  }
  import State._
  val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle  -> Mux(io.wEn, S_Write, S_Idle),
      S_Write -> S_Delay,
      S_Delay -> S_Idle
    )
  )

  backend.io.wEn   := y === S_Write
  backend.io.wAddr := io.wAddr
  backend.io.wData := io.wData
  backend.io.wMask := io.wMask

  io.done := y === S_Delay
}
