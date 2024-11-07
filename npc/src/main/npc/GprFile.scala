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
  val ready  = Output(Bool())
}

class GprFileWriteConn extends Bundle {
  val wEn    = Input(Bool())
  val rdIdx  = Input(UInt(5.W))
  val rdData = Input(UInt(XLen.W))
}

class GprFile extends Module {
  class Port extends Bundle {
    val read  = new GprFileReadConn
    val write = new GprFileWriteConn
    val a0    = Output(UInt(XLen.W))
  }
  val io = IO(new Port)

  private val regs = SRAM(16, UInt(XLen.W), 2, 1, 0)

  regs.readPorts(0).address := io.read.rs1Idx
  regs.readPorts(0).enable  := io.read.valid
  regs.readPorts(1).address := io.read.rs2Idx
  regs.readPorts(1).enable  := io.read.valid

  private object State extends CvtChiselEnum {
    val S_RdIdle   = Value
    val S_RdCommit = Value
  }
  import State._
  private val y = RegInit(S_RdIdle)
  y := MuxLookup(y, S_RdIdle)(
    Seq(
      S_RdIdle   -> Mux(io.read.valid, S_RdCommit, S_RdIdle),
      S_RdCommit -> S_RdIdle
    )
  )

  io.read.ready := y === S_RdCommit

  private val regsRead = Wire(Vec(2, UInt(XLen.W)))
  for (i <- 0 until regsRead.length) {
    regsRead(i) := regs.readPorts(i).data
  }
  private val rsx = RegEnable(regsRead, VecInit(Seq.fill(2)(0.U(XLen.W))), y === S_RdCommit)
  io.read.rs1 := Mux(io.read.rs1Idx.orR, rsx(0), 0.U)
  io.read.rs2 := Mux(io.read.rs2Idx.orR, rsx(1), 0.U)

  regs.writePorts(0).address := io.write.rdIdx
  regs.writePorts(0).data    := io.write.rdData
  regs.writePorts(0).enable  := io.write.wEn & (io.write.rdIdx =/= 0.U)

  private val reg10 =
    RegEnable(io.write.rdData, 0.U(XLen.W), io.write.wEn & io.write.rdIdx === 10.U)
  io.a0 := reg10
}
