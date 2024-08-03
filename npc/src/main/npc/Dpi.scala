package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class DpiBlackBoxIO extends DpiIO {
  val clock = Input(Bool())
}

class DpiBlackBox extends BlackBox with HasBlackBoxInline {
  val io = IO(new DpiBlackBoxIO)

  private val xLenType = getDpiType(XLen.W)
  setInline(
    "DpiBlackBox.sv",
    s"""
       |module DpiBlackBox(
       |  input                 clock,
       |  input                 retired,
       |  input                 ebreak,
       |  input                 bad,
       |  input [${XLen - 1}:0] pc,
       |  input           [7:0] cycles,
       |  input          [31:0] instr,
       |  input [${XLen - 1}:0] a0
       |);
       |  import "DPI-C" function void soc_dpi_ebreak(input bad);
       |  import "DPI-C" function void soc_dpi_report_state(input           retired,
       |                                                    input $xLenType pc,
       |                                                    input byte      cycles,
       |                                                    input int       instr,
       |                                                    input $xLenType a0);
       |
       |  always @(posedge clock) begin
       |    if (retired) begin
       |      if (ebreak) begin
       |        soc_dpi_ebreak(bad);
       |      end
       |    end
       |    soc_dpi_report_state(retired, pc, cycles, instr, a0);
       |  end
       |endmodule
       |""".stripMargin
  )
}

class DpiIO extends Bundle {
  val retired = Input(Bool())
  val ebreak  = Input(Bool())
  val bad     = Input(Bool())
  val pc      = Input(UInt(XLen.W))
  val cycles  = Input(UInt(8.W))
  val instr   = Input(UInt(32.W))
  val a0      = Input(UInt(XLen.W))
}

class Dpi extends Module {
  val io = IO(new DpiIO)

  private val backend = Module(new DpiBlackBox)
  backend.io.clock   := clock.asBool
  backend.io.retired := ~reset.asBool & io.retired
  backend.io.ebreak  := io.ebreak
  backend.io.bad     := ~reset.asBool & io.bad
  backend.io.pc      := io.pc
  backend.io.cycles  := io.cycles
  backend.io.instr   := io.instr
  backend.io.a0      := io.a0
}
