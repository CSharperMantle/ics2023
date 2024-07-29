package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class CoreIO extends Bundle {
  val pc      = Output(UInt(XLen.W))
  val break   = Output(Bool())
  val inval   = Output(Bool())
  val retired = Output(Bool())
  val a0      = Output(UInt(XLen.W))
}

class Core extends Module {
  val io = IO(new CoreIO)

  val csr = Module(new CsrFile)
  val gpr = Module(new GprFile)

  val ifu      = Module(new Ifu)
  val idu      = Module(new Idu)
  val exu      = Module(new Exu)
  val memu     = Module(new Memu)
  val wbu      = Module(new Wbu)
  val pcUpdate = Module(new PcUpdate)

  idu.io.msgIn <> ifu.io.msgOut

  exu.io.msgIn <> idu.io.msgOut
  gpr.io.read <> exu.io.gprRead
  csr.io.conn <> exu.io.csr

  memu.io.msgIn <> exu.io.msgOut

  wbu.io.msgIn <> memu.io.msgOut
  gpr.io.write <> wbu.io.gprWrite

  pcUpdate.io.msgIn <> wbu.io.msgOut

  ifu.io.msgIn <> pcUpdate.io.msgOut

  val retired = pcUpdate.io.msgOut.valid

  io.pc      := ifu.io.msgOut.bits.pc
  io.break   := retired & idu.io.break
  io.inval   := ~reset.asBool & (retired & pcUpdate.io.msgOut.bits.inval)
  io.retired := retired
  io.a0      := gpr.io.a0
}
