package npc

import chisel3._
import chisel3.util._

import npc._

class GprFileIO extends Bundle {
  val rs1Idx = Input(UInt(5.W))
  val rs2Idx = Input(UInt(5.W))
  val wEn    = Input(Bool())
  val rdIdx  = Input(UInt(5.W))
  val rdData = Input(UInt(XLen.W))
  val rs1    = Output(UInt(XLen.W))
  val rs2    = Output(UInt(XLen.W))
  val a0     = Output(UInt(XLen.W))
}

class GprFile extends Module {
  val io = IO(new GprFileIO)

  val regs = Mem(32, UInt(npc.XLen.W))

  io.rs1         := Mux(io.rs1Idx.orR, regs(io.rs1Idx), 0.U)
  io.rs2         := Mux(io.rs2Idx.orR, regs(io.rs2Idx), 0.U)
  regs(io.rdIdx) := Mux(io.wEn, Mux(io.rdIdx.orR, io.rdData, 0.U), regs(io.rdIdx))

  io.a0 := regs(10)
}
