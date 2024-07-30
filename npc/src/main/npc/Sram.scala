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
    "IfuBlackBox.sv",
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
       |      rData = 0;
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
    val S_Idle  = Value
    val S_Read  = Value
    val S_Delay = Value
  }
  import State._
  val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle  -> Mux(io.rEn, S_Read, S_Idle),
      S_Read  -> S_Delay,
      S_Delay -> S_Idle
    )
  )

  val data = RegEnable(backend.io.rData, y === S_Read)

  backend.io.rEn   := y === S_Read
  backend.io.rAddr := io.rAddr

  io.rData := data
  io.done  := y === S_Delay
}
