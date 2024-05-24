package npc

import chisel3._
import chisel3.util._

class GPRFileIO extends Bundle {
  val rs1_addr = Input(UInt(5.W))
  val rs2_addr = Input(UInt(5.W))
  val wen      = Input(Bool())
  val rd_addr  = Input(UInt(5.W))
  val rd_data  = Input(UInt(npc.XLen.W))
  val rs1      = Output(UInt(npc.XLen.W))
  val rs2      = Output(UInt(npc.XLen.W))
}

class GPRFile extends Module {
  val io = IO(new GPRFileIO())

  val regs = Mem(32, UInt(npc.XLen.W))

  io.rs1           := Mux(io.rs1_addr.orR, regs(io.rs1_addr), 0.U)
  io.rs2           := Mux(io.rs2_addr.orR, regs(io.rs2_addr), 0.U)
  regs(io.rd_addr) := Mux(io.wen, Mux(io.rd_addr.orR, io.rd_data, 0.U), regs(io.rd_addr))
}
