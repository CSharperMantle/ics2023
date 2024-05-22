import chisel3._
import chisel3.util._

import npc._

class TopIO extends Bundle {
  val i_inst     = Input(UInt(npc.XLen.W))
  val o_pc       = Output(UInt(npc.XLen.W))
  val i_addr_rs1 = Input(UInt(5.W))
  val i_addr_rs2 = Input(UInt(5.W))
  val i_wen      = Input(Bool())
  val i_addr_rd  = Input(UInt(5.W))
  val i_data_rd  = Input(UInt(npc.XLen.W))
  val o_rs1      = Output(UInt(npc.XLen.W))
  val o_rs2      = Output(UInt(npc.XLen.W))
}

class Top extends Module {
  val io   = IO(new TopIO)
  val core = Module(new NewProcCore)
  core.io.i_inst     := io.i_inst
  core.io.i_addr_rs1 := io.i_addr_rs1
  core.io.i_addr_rs2 := io.i_addr_rs2
  core.io.i_wen      := io.i_wen
  core.io.i_addr_rd  := io.i_addr_rd
  core.io.i_data_rd  := io.i_data_rd
  io.o_pc            := core.io.o_pc
  io.o_rs1           := core.io.o_rs1
  io.o_rs2           := core.io.o_rs2

}
