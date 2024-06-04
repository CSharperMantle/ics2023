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
  val MemRd   = Value
  val MemRdu  = Value
  val MemWt   = Value
  val MemNone = Value
}

class MemuBlackBoxIO extends Bundle {
  val memMask  = Input(UInt(8.W))
  val memREn   = Input(Bool())
  val memRAddr = Input(UInt(XLen.W))
  val memRData = Output(UInt(XLen.W))
  val memWEn   = Input(Bool())
  val memWAddr = Input(UInt(XLen.W))
  val memWData = Input(UInt(XLen.W))
}

class MemuBlackBox extends BlackBox with HasBlackBoxInline {
  val io = IO(new MemuBlackBoxIO)

  val xLenType = if (XLen == 32) "int" else "longint"

  setInline(
    "MemuBlackBox.sv",
    s"""
       |import "DPI-C" function $xLenType npc_dpi_pmem_read(input $xLenType mem_r_addr);
       |
       |import "DPI-C" function void npc_dpi_pmem_write(input byte      mem_mask,
       |                                                input $xLenType mem_w_addr,
       |                                                input $xLenType mem_w_data);
       |
       |module MemuBlackBox(
       |  input      [7:0]                memMask,
       |  input                           memREn,
       |  input      [${XLen - 1}:0]      memRAddr,
       |  output reg [${XLen - 1}:0]      memRData,
       |  input                           memWEn,
       |  input      [${XLen - 1}:0]      memWAddr,
       |  input      [${XLen - 1}:0]      memWData
       |);
       |  always @(*) begin
       |    if (memREn) begin
       |      memRData = npc_dpi_pmem_read(memRAddr);
       |    end else begin
       |      memRData = 0;
       |    end
       |    if (memWEn) begin
       |      npc_dpi_pmem_write(memMask, memWAddr, memWData);
       |    end
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
  val inval     = Output(Bool())
}

class Memu extends Module {
  require(XLen == 32, "Memu for RV64 is not implemented")

  val io = IO(new MemuIO)

  val memActionDec = Decoder1H(
    Seq(
      MemAction.MemRd.BP   -> 0,
      MemAction.MemRdu.BP  -> 1,
      MemAction.MemWt.BP   -> 2,
      MemAction.MemNone.BP -> 3
    )
  )
  val memAction1H = memActionDec(io.memAction)

  val memWidthDec = Decoder1H(
    Seq(
      MemWidth.LenB.BP -> 0,
      MemWidth.LenH.BP -> 1,
      MemWidth.LenW.BP -> 2
    )
  )
  val memWidth1H = memWidthDec(io.memWidth)
  val memMask = Mux1H(
    Seq(
      memWidth1H(0) -> "b00000001".U(8.W),
      memWidth1H(1) -> "b00000011".U(8.W),
      memWidth1H(2) -> "b00001111".U(8.W),
      memWidth1H(3) -> 0.U(8.W)
    )
  )

  val memAlignDec = Decoder1H(
    Seq(
      "b00".BP -> 0,
      "b01".BP -> 1,
      "b10".BP -> 2,
      "b11".BP -> 3
    )
  )

  val wEn = memAction1H(2)
  val rEn = memAction1H(0) | memAction1H(1)

  val backend = Module(new MemuBlackBox)

  backend.io.memREn   := rEn
  backend.io.memRAddr := Cat(io.memRAddr(XLen - 1, 2), Fill(2, 0.B))

  val memWAlign1H = memAlignDec(io.memWAddr(1, 0))
  val memWDataShifted = Mux1H(
    Seq(
      memWAlign1H(0) -> io.memWData,
      memWAlign1H(1) -> Cat(io.memWData(XLen - 9, 0), Fill(8, 0.B)),
      memWAlign1H(2) -> Cat(io.memWData(XLen - 17, 0), Fill(16, 0.B)),
      memWAlign1H(3) -> Cat(io.memWData(XLen - 25, 0), Fill(24, 0.B)),
      memWAlign1H(4) -> 0.U
    )
  )
  backend.io.memWData := memWDataShifted
  backend.io.memWAddr := Cat(io.memWAddr(XLen - 1, 2), Fill(2, 0.B))
  backend.io.memWEn   := wEn
  val memWMaskShifted = Mux1H(
    Seq(
      memWAlign1H(0) -> memMask,
      memWAlign1H(1) -> Cat(memMask(6, 0), Fill(1, 0.B)),
      memWAlign1H(2) -> Cat(memMask(5, 0), Fill(2, 0.B)),
      memWAlign1H(3) -> Cat(memMask(4, 0), Fill(3, 0.B)),
      memWAlign1H(4) -> 0.U
    )
  )

  backend.io.memMask := MuxCase(
    0.U,
    Seq(
      rEn -> memMask,
      wEn -> memWMaskShifted
    )
  )

  val memRAlign1H = memAlignDec(io.memRAddr(1, 0))
  val memRDataShifted = Mux1H(
    Seq(
      memRAlign1H(0) -> backend.io.memRData,
      memRAlign1H(1) -> Cat(Fill(8, 0.B), backend.io.memRData(XLen - 1, 8)),
      memRAlign1H(2) -> Cat(Fill(16, 0.B), backend.io.memRData(XLen - 1, 16)),
      memRAlign1H(3) -> Cat(Fill(24, 0.B), backend.io.memRData(XLen - 1, 24)),
      memRAlign1H(4) -> 0.U
    )
  )

  val sext = Module(new SExtender)
  sext.io.sextData := memRDataShifted
  sext.io.sextW    := io.memWidth
  sext.io.sextU    := memAction1H(1)
  io.memRData      := sext.io.sextRes

  io.inval := memAction1H(memActionDec.bitBad) | memWAlign1H(
    memAlignDec.bitBad
  ) | memRAlign1H(memAlignDec.bitBad)
}
