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
       |  input                 ebreak,
       |  input                 retired,
       |  input [${XLen - 1}:0] pc,
       |  input          [15:0] cycles,
       |  input          [31:0] instr,
       |  input [${XLen - 1}:0] a0,
       |  input                 memEn,
       |  input [${XLen - 1}:0] rwAddr,
       |  input                 bad
       |);
       |  import "DPI-C" function void soc_dpi_ebreak();
       |  import "DPI-C" function void soc_dpi_report_state(input           retired,
       |                                                    input $xLenType pc,
       |                                                    input shortint  cycles,
       |                                                    input int       instr,
       |                                                    input $xLenType a0,
       |                                                    input           memEn,
       |                                                    input $xLenType rwAddr,
       |                                                    input           bad);
       |
       |  always @(posedge clock) begin
       |    if (retired) begin
       |      if (ebreak) begin
       |        soc_dpi_ebreak();
       |      end
       |    end
       |    soc_dpi_report_state(retired, pc, cycles, instr, a0, memEn, rwAddr, bad);
       |  end
       |endmodule
       |""".stripMargin
  )
}

class DpiIO extends Bundle {
  val retired = Input(Bool())
  val pc      = Input(UInt(XLen.W))
  val ebreak  = Input(Bool())
  val cycles  = Input(UInt(16.W))
  val instr   = Input(UInt(32.W))
  val a0      = Input(UInt(XLen.W))
  val memEn   = Input(Bool())
  val rwAddr  = Input(UInt(XLen.W))
  val bad     = Input(Bool())
}

class Dpi extends Module {
  val io = IO(new DpiIO)

  private val backend = Module(new DpiBlackBox)
  backend.io.clock   := clock.asBool
  backend.io.retired := ~reset.asBool & io.retired
  backend.io.pc      := io.pc
  backend.io.ebreak  := io.ebreak
  backend.io.cycles  := io.cycles
  backend.io.instr   := io.instr
  backend.io.a0      := io.a0
  backend.io.memEn   := io.memEn
  backend.io.rwAddr  := io.rwAddr
  backend.io.bad     := ~reset.asBool & io.bad
}
