package npc

import chisel3._
import chisel3.util._

import common._

class NewProcCoreIO extends Bundle {
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

class NewProcCore extends Module {
  val io = IO(new NewProcCoreIO)

  val w_update_pc = Wire(Bool())
  val w_dnpc      = Wire(UInt(npc.XLen.W))
  val reg_pc      = RegEnable(w_dnpc, npc.InitPCVal, w_update_pc)

  val gpr = Module(new GPRFile)
  gpr.io.i_addr_rs1 := io.i_addr_rs1
  gpr.io.i_addr_rs2 := io.i_addr_rs2
  gpr.io.i_wen      := io.i_wen
  gpr.io.i_addr_rd  := io.i_addr_rd
  gpr.io.i_data_rd  := io.i_data_rd
  io.o_rs1          := gpr.io.o_rs1
  io.o_rs2          := gpr.io.o_rs2

  w_update_pc := true.B
  w_dnpc      := reg_pc + 4.U

  io.o_pc := reg_pc
}
