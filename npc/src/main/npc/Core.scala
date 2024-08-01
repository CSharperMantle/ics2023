package npc

import chisel3._
import chisel3.util._

import common._
import npc._
import device._

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

  val readArb = Module(
    new GenericArbiter(new MemReadReq(XLen.W), new MemReadResp(32.W), 2)
  )
  readArb.io.masterReq(0)  <> ifu.io.rReq
  readArb.io.masterResp(0) <> ifu.io.rResp
  readArb.io.masterReq(1)  <> lsu.io.rReq
  readArb.io.masterResp(1) <> lsu.io.rResp

  val memRXbar = Module(
    new Xbar(
      new MemReadReq(XLen.W),
      new MemReadResp(XLen.W),
      Seq(
        Seq("b00000010_00000000_0???????_????????".BP, "b00000010_00000000_10??????_????????".BP),
        Seq("b10000000_????????_????????_????????".BP)
      ),
      (req: MemReadReq) => req.addr,
      (resp: MemReadResp) => resp.rResp
    )
  )
  memRXbar.io.masterReq  <> readArb.io.slaveReq
  memRXbar.io.masterResp <> readArb.io.slaveResp

  val clint = Module(new Clint)
  clint.io.rReq  <> memRXbar.io.slaveReq(0)
  clint.io.rResp <> memRXbar.io.slaveResp(0)

  val memRPort = Module(new SramRPort(XLen.W, 32.W))
  memRPort.io.req  <> memRXbar.io.slaveReq(1)
  memRPort.io.resp <> memRXbar.io.slaveResp(1)

  val memWXbar = Module(
    new Xbar(
      new MemWriteReq(XLen.W, 32.W),
      new MemWriteResp,
      Seq(
        Seq("b00010000_00000000_0000????_????????".BP),
        Seq("b10000000_????????_????????_????????".BP)
      ),
      (req: MemWriteReq) => req.wAddr,
      (resp: MemWriteResp) => resp.bResp
    )
  )
  memWXbar.io.masterReq  <> lsu.io.wReq
  memWXbar.io.masterResp <> lsu.io.wResp

  val uart = Module(new Uart)
  uart.io.req  <> memWXbar.io.slaveReq(0)
  uart.io.resp <> memWXbar.io.slaveResp(0)

  val memWPort = Module(new SramWPort(XLen.W, 32.W))
  memWPort.io.req  <> memWXbar.io.slaveReq(1)
  memWPort.io.resp <> memWXbar.io.slaveResp(1)

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
