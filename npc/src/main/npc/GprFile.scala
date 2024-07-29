package npc

import chisel3._
import chisel3.util._

import npc._

class GprFileReadConn extends Bundle {
  val rs1Idx = Input(UInt(5.W))
  val rs2Idx = Input(UInt(5.W))
  val rs1    = Output(UInt(XLen.W))
  val rs2    = Output(UInt(XLen.W))
}

class GprFileWriteConn extends Bundle {
  val wEn    = Input(Bool())
  val rdIdx  = Input(UInt(5.W))
  val rdData = Input(UInt(XLen.W))
}

class GprFileIO extends Bundle {
  val read  = new GprFileReadConn
  val write = new GprFileWriteConn
  val a0    = Output(UInt(XLen.W))
}

class GprFile extends Module {
  val io = IO(new GprFileIO)

  val regs = Mem(32, UInt(npc.XLen.W))

  io.read.rs1 := Mux(io.read.rs1Idx.orR, regs(io.read.rs1Idx), 0.U)
  io.read.rs2 := Mux(io.read.rs2Idx.orR, regs(io.read.rs2Idx), 0.U)
  regs(io.write.rdIdx) := Mux(
    io.write.wEn,
    Mux(io.write.rdIdx.orR, io.write.rdData, 0.U),
    regs(io.write.rdIdx)
  )

  io.a0 := regs(10)
}
