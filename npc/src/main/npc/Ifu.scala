package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class IfuBlackBoxIO extends Bundle {
  val clock = Input(Clock())
  val pc    = Input(UInt(XLen.W))
  val instr = Output(UInt(32.W))
}

class IfuBlackBox extends BlackBox with HasBlackBoxInline {
  val io = IO(new IfuBlackBoxIO)

  val xLenType = if (XLen == 32) "int" else "longint"

  setInline(
    "IfuBlackBox.sv",
    s"""
       |import "DPI-C" function void npc_dpi_ifu(input  $xLenType pc,
       |                                         output int       instr);
       |
       |module IfuBlackBox(
       |  input                  clock,
       |  input  [${XLen - 1}:0] pc,
       |  output [31:0]          instr
       |);
       |  always @(posedge clock) begin
       |    npc_dpi_ifu(pc, instr);
       |  end
       |endmodule
       |""".stripMargin
  )
}

class IfuIO extends Bundle {
  val pc    = Input(UInt(XLen.W))
  val instr = Output(UInt(32.W))
}

class Ifu extends Module {
  val io = IO(new IfuIO)

  val backend = Module(new IfuBlackBox)
  backend.io.clock := clock
  backend.io.pc    := io.pc
  io.instr         := backend.io.instr
}
