package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class GprFileReadConn extends Bundle {
  val valid  = Input(Bool())
  val rs1Idx = Input(UInt(5.W))
  val rs2Idx = Input(UInt(5.W))
  val rs1    = Output(UInt(XLen.W))
  val rs2    = Output(UInt(XLen.W))
}

class GprFileWriteConn extends Bundle {
  val valid  = Input(Bool())
  val wEn    = Input(Bool())
  val rdIdx  = Input(UInt(5.W))
  val rdData = Input(UInt(XLen.W))
}

class GprFile extends Module {
  class Port extends Bundle {
    val read  = new GprFileReadConn
    val write = new GprFileWriteConn
  }
  val io = IO(new Port)

  private val gprs = SRAM(16, UInt(XLen.W), 2, 1, 0)

  gprs.readPorts(0).address := io.read.rs1Idx
  gprs.readPorts(0).enable  := io.read.valid
  gprs.readPorts(1).address := io.read.rs2Idx
  gprs.readPorts(1).enable  := io.read.valid

  io.read.rs1 := Mux(io.read.rs1Idx === 0.U, 0.U, gprs.readPorts(0).data)
  io.read.rs2 := Mux(io.read.rs2Idx === 0.U, 0.U, gprs.readPorts(1).data)

  gprs.writePorts(0).address := io.write.rdIdx
  gprs.writePorts(0).data    := io.write.rdData
  gprs.writePorts(0).enable  := io.write.valid & io.write.wEn & (io.write.rdIdx =/= 0.U)
}
