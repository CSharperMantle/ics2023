package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class IfuBlackBoxIO extends Bundle {
  val clk   = Input(Bool())
  val en    = Input(Bool())
  val pc    = Input(UInt(XLen.W))
  val instr = Output(UInt(32.W))
  val valid = Output(Bool())
}

class IfuBlackBox extends BlackBox with HasBlackBoxInline {
  val io = IO(new IfuBlackBoxIO)

  val xLenType = if (XLen == 32) "int" else "longint"
  setInline(
    "IfuBlackBox.sv",
    s"""
       |import "DPI-C" function bit npc_dpi_ifu(input  $xLenType pc,
       |                                        output int       instr);
       |
       |module IfuBlackBox(
       |  input                      clk,
       |  input                      en,
       |  input      [${XLen - 1}:0] pc,
       |  output reg [31:0]          instr,
       |  output reg                 valid
       |);
       |  always @(posedge clk) begin
       |    if (en) begin
       |      valid <= npc_dpi_ifu(pc, instr);
       |    end else begin
       |      valid <= 1'b0;
       |    end
       |  end
       |endmodule
       |""".stripMargin
  )
}

class Ifu2IduMsg extends Bundle {
  val instr = Output(UInt(32.W))
  // Pass-through for Idu
  val pc = Output(UInt(XLen.W))
}

class IfuIO extends Bundle {
  val msgIn  = Flipped(Decoupled(new PcUpdate2IfuMsg))
  val msgOut = Decoupled(new Ifu2IduMsg)
}

class Ifu extends Module {
  val io = IO(new IfuIO)

  val pc = RegEnable(io.msgIn.bits.dnpc, InitPCVal.U(XLen.W), io.msgIn.valid)

  val backend = Module(new IfuBlackBox)
  backend.io.clk := clock.asBool
  backend.io.pc  := pc

  object States extends CvtChiselEnum {
    val S_Idle      = Value
    val S_WaitReady = Value
  }
  import States._
  val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle      -> Mux(backend.io.valid, S_WaitReady, S_Idle),
      S_WaitReady -> Mux(io.msgOut.ready, S_Idle, S_WaitReady)
    )
  )

  backend.io.en := ~reset.asBool & (y === S_Idle)

  io.msgOut.bits.instr := backend.io.instr
  io.msgOut.bits.pc    := pc

  io.msgIn.ready  := y === S_WaitReady
  io.msgOut.valid := y === S_WaitReady
}
