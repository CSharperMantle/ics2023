package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class CoreIO extends Bundle {
  val pc          = Output(UInt(XLen.W))
  val instr       = Output(UInt(32.W))
  val break       = Output(Bool())
  val inval       = Output(Bool())
  val retired     = Output(Bool())
  val instrCycles = Output(UInt(8.W))
  val a0          = Output(UInt(XLen.W))
}

class Core extends Module {
  val io = IO(new CoreIO)

  val csr = Module(new CsrFile)
  val gpr = Module(new GprFile)

  val ifu      = Module(new Ifu)
  val idu      = Module(new Idu)
  val exu      = Module(new Exu)
  val lsu      = Module(new Lsu)
  val wbu      = Module(new Wbu)
  val pcUpdate = Module(new PcUpdate)

  val memRPort = Module(new SramRPort(XLen.W, 32.W))
  val readArb = Module(
    new GenericArbiter(new SramRPortReq(XLen.W), new SramRPortResp(32.W), 2)
  )
  readArb.io.slaveReq      <> memRPort.io.req
  readArb.io.slaveResp     <> memRPort.io.resp
  readArb.io.masterReq(0)  <> ifu.io.rReq
  readArb.io.masterResp(0) <> ifu.io.rResp
  readArb.io.masterReq(1)  <> lsu.io.rReq
  readArb.io.masterResp(1) <> lsu.io.rResp

  val memWPort = Module(new SramWPort(XLen.W, 32.W))
  memWPort.io.req  <> lsu.io.wReq
  memWPort.io.resp <> lsu.io.wResp

  StageConnect(idu.io.msgIn, ifu.io.msgOut)
  StageConnect(exu.io.msgIn, idu.io.msgOut)
  gpr.io.read <> exu.io.gprRead
  csr.io.conn <> exu.io.csrConn
  StageConnect(lsu.io.msgIn, exu.io.msgOut)
  StageConnect(wbu.io.msgIn, lsu.io.msgOut)
  gpr.io.write <> wbu.io.gprWrite
  StageConnect(pcUpdate.io.msgIn, wbu.io.msgOut)
  StageConnect(ifu.io.msgIn, pcUpdate.io.msgOut)

  val retired = pcUpdate.io.msgOut.valid

  val instrCycles = RegNext(0.U(8.W))

  instrCycles := Mux(retired, 0.U, instrCycles + 1.U)

  io.pc          := ifu.io.msgOut.bits.pc
  io.instr       := ifu.io.msgOut.bits.instr
  io.break       := retired & idu.io.break
  io.inval       := ~reset.asBool & (retired & pcUpdate.io.msgOut.bits.inval)
  io.retired     := retired
  io.instrCycles := instrCycles
  io.a0          := gpr.io.a0
}
