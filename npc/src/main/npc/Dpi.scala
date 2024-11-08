package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class DpiBlackBox extends BlackBox with HasBlackBoxInline {
  class Port extends DpiIO {
    val clock = Input(Bool())
    val reset = Input(Bool())
  }
  val io = IO(new Port)

  private val xLenType = getDpiType(XLen.W)
  setInline(
    "DpiBlackBox.sv",
    s"""
       |module DpiBlackBox(
       |  input                 ebreak,
       |  input                 retired,
       |  input [${XLen - 1}:0] pc,
       |  input          [31:0] instr,
       |  input                 memEn,
       |  input [${XLen - 1}:0] rwAddr,
       |  input                 bad,
       |  input                 clock,
       |  input                 reset
       |);
       |`ifdef VERILATOR
       |  import "DPI-C" function void soc_dpi_ebreak();
       |  import "DPI-C" function void soc_dpi_report_state(input           retired,
       |                                                    input $xLenType pc,
       |                                                    input shortint  instr_cycles,
       |                                                    input int       instr,
       |                                                    input           memEn,
       |                                                    input $xLenType rwAddr,
       |                                                    input           bad);
       |
       |  reg [15:0] instr_cycles;
       |  always @(posedge clock) begin
       |    if (reset) begin
       |      instr_cycles <= 16'h0;
       |    end else begin
       |      instr_cycles <= retired ? 16'h0 : (instr_cycles + 16'h1);
       |    end
       |  end
       |
       |  always @(posedge clock) begin
       |    if (retired) begin
       |      if (ebreak) begin
       |        soc_dpi_ebreak();
       |      end
       |    end
       |    soc_dpi_report_state(retired, pc, instr_cycles, instr, memEn, rwAddr, bad);
       |  end
       |`endif
       |endmodule
       |""".stripMargin
  )
}

class DpiIO extends Bundle {
  val retired = Input(Bool())
  val pc      = Input(UInt(XLen.W))
  val ebreak  = Input(Bool())
  val instr   = Input(UInt(32.W))
  val memEn   = Input(Bool())
  val rwAddr  = Input(UInt(XLen.W))
  val bad     = Input(Bool())
}

class Dpi extends Module {
  val io = IO(new DpiIO)

  private val backend = Module(new DpiBlackBox)
  backend.io.retired := ~reset.asBool & io.retired
  backend.io.pc      := io.pc
  backend.io.ebreak  := io.ebreak
  backend.io.instr   := io.instr
  backend.io.memEn   := io.memEn
  backend.io.rwAddr  := io.rwAddr
  backend.io.bad     := ~reset.asBool & io.bad
  backend.io.clock   := clock.asBool
  backend.io.reset   := reset.asBool
}
