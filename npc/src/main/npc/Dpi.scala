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
       |  input                 ifuInValid,
       |  input                 iduInValid,
       |  input                 exuInValid,
       |  input                 lsuInValid,
       |  input                 wbuInValid,
       |  input                 ifuOutValid,
       |  input                 iduOutValid,
       |  input                 exuOutValid,
       |  input                 lsuOutValid,
       |  input                 wbuOutValid,
       |  input                 icacheHit,
       |  input                 icacheMiss,
       |  input                 predHit,
       |  input                 predMiss,
       |  input                 clock,
       |  input                 reset
       |);
       |`ifdef VERILATOR
       |  import "DPI-C" function void soc_dpi_ebreak();
       |  import "DPI-C" function void soc_dpi_report_state(input $xLenType pc,
       |                                                    input int       instr,
       |                                                    input int       icache_hit_count,
       |                                                    input int       icache_miss_count,
       |                                                    input int       pred_hit_count,
       |                                                    input int       pred_miss_count,
       |                                                    input shortint  instr_cycles,
       |                                                    input shortint  ifu_cycles,
       |                                                    input shortint  idu_cycles,
       |                                                    input shortint  exu_cycles,
       |                                                    input shortint  lsu_cycles,
       |                                                    input shortint  wbu_cycles,
       |                                                    input           memEn,
       |                                                    input $xLenType rwAddr,
       |                                                    input           bad,
       |                                                    input           retired);
       |
       |  reg [15:0] instr_cycles;
       |  reg [15:0] ifu_cycles;
       |  reg [15:0] idu_cycles;
       |  reg [15:0] exu_cycles;
       |  reg [15:0] lsu_cycles;
       |  reg [15:0] wbu_cycles;
       |  reg [31:0] icache_hit_count;
       |  reg [31:0] icache_miss_count;
       |  reg [31:0] pred_hit_count;
       |  reg [31:0] pred_miss_count;
       |  always @(posedge clock) begin
       |    if (reset) begin
       |      instr_cycles <= 16'h0;
       |      ifu_cycles <= 16'h0;
       |      idu_cycles <= 16'h0;
       |      exu_cycles <= 16'h0;
       |      lsu_cycles <= 16'h0;
       |      wbu_cycles <= 16'h0;
       |      icache_hit_count <= 32'h0;
       |      icache_miss_count <= 32'h0;
       |      pred_hit_count <= 32'h0;
       |      pred_miss_count <= 32'h0;
       |    end else begin
       |      instr_cycles <= retired ? 16'h0 : (instr_cycles + 1);
       |      ifu_cycles <= retired ? 16'h0 : (ifuOutValid ? ifu_cycles : (ifuInValid ? (ifu_cycles + 1) : 0));
       |      idu_cycles <= retired ? 16'h0 : (iduOutValid ? idu_cycles : (iduInValid ? (idu_cycles + 1) : 0));
       |      exu_cycles <= retired ? 16'h0 : (exuOutValid ? exu_cycles : (exuInValid ? (exu_cycles + 1) : 0));
       |      lsu_cycles <= retired ? 16'h0 : (lsuOutValid ? lsu_cycles : (lsuInValid ? (lsu_cycles + 1) : 0));
       |      wbu_cycles <= retired ? 16'h0 : (wbuOutValid ? wbu_cycles : (wbuInValid ? (wbu_cycles + 1) : 0));
       |      icache_hit_count <= icacheHit ? (icache_hit_count + 1) : icache_hit_count;
       |      icache_miss_count <= icacheMiss ? (icache_miss_count + 1) : icache_miss_count;
       |      pred_hit_count <= predHit ? (pred_hit_count + 1) : pred_hit_count;
       |      pred_miss_count <= predMiss ? (pred_miss_count + 1) : pred_miss_count;
       |    end
       |  end
       |
       |  always @(posedge clock) begin
       |    if (retired) begin
       |      if (ebreak) begin
       |        soc_dpi_ebreak();
       |      end
       |    end
       |    soc_dpi_report_state(pc,
       |                         instr,
       |                         icache_hit_count,
       |                         icache_miss_count,
       |                         pred_hit_count,
       |                         pred_miss_count,
       |                         instr_cycles,
       |                         ifu_cycles,
       |                         idu_cycles,
       |                         exu_cycles,
       |                         lsu_cycles,
       |                         wbu_cycles,
       |                         memEn,
       |                         rwAddr,
       |                         bad,
       |                         retired);
       |  end
       |`endif
       |endmodule
       |""".stripMargin
  )
}

class DpiIO extends Bundle {
  val retired     = Input(Bool())
  val pc          = Input(UInt(XLen.W))
  val ebreak      = Input(Bool())
  val instr       = Input(UInt(32.W))
  val memEn       = Input(Bool())
  val rwAddr      = Input(UInt(XLen.W))
  val bad         = Input(Bool())
  val ifuInValid  = Input(Bool())
  val iduInValid  = Input(Bool())
  val exuInValid  = Input(Bool())
  val lsuInValid  = Input(Bool())
  val wbuInValid  = Input(Bool())
  val ifuOutValid = Input(Bool())
  val iduOutValid = Input(Bool())
  val exuOutValid = Input(Bool())
  val lsuOutValid = Input(Bool())
  val wbuOutValid = Input(Bool())
  val icacheHit   = Input(Bool())
  val icacheMiss  = Input(Bool())
  val predHit     = Input(Bool())
  val predMiss    = Input(Bool())
}

class Dpi extends Module {
  val io = IO(new DpiIO)

  private val backend = Module(new DpiBlackBox)
  backend.io.retired     := ~reset.asBool & io.retired
  backend.io.pc          := io.pc
  backend.io.ebreak      := io.ebreak
  backend.io.instr       := io.instr
  backend.io.memEn       := io.memEn
  backend.io.rwAddr      := io.rwAddr
  backend.io.bad         := ~reset.asBool & io.bad
  backend.io.ifuInValid  := io.ifuInValid
  backend.io.iduInValid  := io.iduInValid
  backend.io.exuInValid  := io.exuInValid
  backend.io.lsuInValid  := io.lsuInValid
  backend.io.wbuInValid  := io.wbuInValid
  backend.io.ifuOutValid := io.ifuOutValid
  backend.io.iduOutValid := io.iduOutValid
  backend.io.exuOutValid := io.exuOutValid
  backend.io.lsuOutValid := io.lsuOutValid
  backend.io.wbuOutValid := io.wbuOutValid
  backend.io.icacheHit   := io.icacheHit
  backend.io.icacheMiss  := io.icacheMiss
  backend.io.predHit     := io.predHit
  backend.io.predMiss    := io.predMiss
  backend.io.clock       := clock.asBool
  backend.io.reset       := reset.asBool
}
