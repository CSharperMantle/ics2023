package npc

import chisel3._
import chisel3.util._

class GPRFileIO extends Bundle {
  val i_addr_rs1 = Input(UInt(5.W))
  val i_addr_rs2 = Input(UInt(5.W))
  val i_wen      = Input(Bool())
  val i_addr_rd  = Input(UInt(5.W))
  val i_data_rd  = Input(UInt(npc.XLen.W))
  val o_rs1      = Output(UInt(npc.XLen.W))
  val o_rs2      = Output(UInt(npc.XLen.W))
}

class GPRFile extends Module {
  val io = IO(new GPRFileIO())

  val regs = Mem(32, UInt(npc.XLen.W))

  io.o_rs1           := Mux(io.i_addr_rs1.orR, regs(io.i_addr_rs1), 0.U)
  io.o_rs2           := Mux(io.i_addr_rs2.orR, regs(io.i_addr_rs2), 0.U)
  regs(io.i_addr_rd) := Mux(io.i_wen, Mux(io.i_addr_rd.orR, io.i_data_rd, 0.U), regs(io.i_addr_rd))
}
