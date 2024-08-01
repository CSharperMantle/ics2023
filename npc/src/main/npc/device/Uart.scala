package npc.device

import chisel3._
import chisel3.util._

import _root_.npc._
import common._
import npc._

class UartBlackBoxIO extends Bundle {
  val en = Input(Bool())
  val ch = Input(UInt(8.W))
}

class UartBlackBox extends BlackBox with HasBlackBoxInline {
  val io = IO(new UartBlackBoxIO)

  setInline(
    "UartBlackBox.sv",
    s"""
       |module UartBlackBox(
       |  input       en,
       |  input [7:0] ch
       |  
       |);
       |  always @(*) begin
       |    if (en) begin
       |      $$write("%c", ch);
       |    end
       |  end
       |endmodule
       |""".stripMargin
  )
}

class UartIO extends Bundle {
  val req  = Flipped(Irrevocable(new MemWriteReq(XLen.W, 32.W)))
  val resp = Irrevocable(new MemWriteResp)
}

class Uart extends Module {
  val io = IO(new UartIO)

  private val backend = Module(new UartBlackBox)

  private val addr = RegInit(0.U)
  addr := Mux(io.req.valid, io.req.bits.wAddr, Mux(io.resp.ready, 0.U, addr))

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

  private val addrBad = ~("b00010000_00000000_00000000_00000000".BP === addr)

  backend.io.en := y === S_Write
  backend.io.ch := Mux(io.req.bits.wMask(0), io.req.bits.wData(7, 0), 0.U)

  io.req.ready := y === S_WaitReady

  io.resp.valid      := y === S_WaitReady
  io.resp.bits.bResp := Mux(addrBad, BResp.DecErr.U, BResp.Okay.U)
}
