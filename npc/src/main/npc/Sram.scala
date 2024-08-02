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

  private val addrType     = getDpiType(addrWidth)
  private val addrWidthVal = addrWidth.asInstanceOf[KnownWidth].value
  private val dataType     = getDpiType(dataWidth)
  private val dataWidthVal = dataWidth.asInstanceOf[KnownWidth].value
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
       |      rData = ${dataWidthVal}'hdead3210;
       |    end
       |  end
       |endmodule
       |""".stripMargin
  )
}

class SramRPortIO(addrWidth: Width, dataWidth: Width) extends Bundle {
  val req  = Flipped(Irrevocable(new MemReadReq(addrWidth)))
  val resp = Irrevocable(new MemReadResp(dataWidth))
}

class SramRPort(addrWidth: Width, dataWidth: Width) extends Module {
  val io = IO(new SramRPortIO(addrWidth, dataWidth))

  private val backend = Module(new SramRPortBlackBox(addrWidth, dataWidth))

  private object State extends CvtChiselEnum {
    val S_Idle      = Value
    val S_Read      = Value
    val S_WaitReady = Value
  }
  import State._
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle      -> Mux(io.req.valid, S_Read, S_Idle),
      S_Read      -> S_WaitReady,
      S_WaitReady -> Mux(io.resp.ready, S_Idle, S_WaitReady)
    )
  )

  private val addr = RegEnable(io.req.bits.addr, io.req.valid)
  private val data = RegEnable(backend.io.rData, y === S_Read)

  backend.io.rEn   := y === S_Read
  backend.io.rAddr := addr

  io.req.ready := y === S_Read

  io.resp.bits.data  := data
  io.resp.bits.rResp := RResp.Okay.U
  io.resp.valid      := y === S_WaitReady
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
  val req  = Flipped(Irrevocable(new MemWriteReq(addrWidth, dataWidth)))
  val resp = Irrevocable(new MemWriteResp)
}

class SramWPort(addrWidth: Width, dataWidth: Width) extends Module {
  val io = IO(new SramWPortIO(addrWidth, dataWidth))

  private val backend = Module(new SramWPortBlackBox(addrWidth, dataWidth))

  private object State extends CvtChiselEnum {
    val S_Idle      = Value
    val S_Write     = Value
    val S_WaitReady = Value
  }
  import State._
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle      -> Mux(io.req.valid, S_Write, S_Idle),
      S_Write     -> S_WaitReady,
      S_WaitReady -> Mux(io.resp.ready, S_Idle, S_WaitReady)
    )
  )

  backend.io.wEn   := y === S_Write
  backend.io.wAddr := io.req.bits.wAddr
  backend.io.wData := io.req.bits.wData
  backend.io.wMask := io.req.bits.wMask

  io.req.ready := y === S_Write

  io.resp.valid      := y === S_WaitReady
  io.resp.bits.bResp := BResp.Okay.U
}
