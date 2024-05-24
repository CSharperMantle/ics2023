package npc

import chisel3._
import chisel3.util._

class GPRFileIO extends Bundle {
  val rs1Addr = Input(UInt(5.W))
  val rs2Addr = Input(UInt(5.W))
  val wEn     = Input(Bool())
  val rdAddr  = Input(UInt(5.W))
  val rdData  = Input(UInt(npc.XLen.W))
  val rs1     = Output(UInt(npc.XLen.W))
  val rs2     = Output(UInt(npc.XLen.W))
}

class GPRFile extends Module {
  val io = IO(new GPRFileIO())

  val regs = Mem(32, UInt(npc.XLen.W))

  io.rs1          := Mux(io.rs1Addr.orR, regs(io.rs1Addr), 0.U)
  io.rs2          := Mux(io.rs2Addr.orR, regs(io.rs2Addr), 0.U)
  regs(io.rdAddr) := Mux(io.wEn, Mux(io.rdAddr.orR, io.rdData, 0.U), regs(io.rdAddr))
}
