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

  val readArb = Module(
    new GenericArbiter(new SramRPortReq(XLen.W), new SramRPortResp(32.W), 2)
  )
  readArb.io.masterReq(0)  <> ifu.io.rReq
  readArb.io.masterResp(0) <> ifu.io.rResp
  readArb.io.masterReq(1)  <> lsu.io.rReq
  readArb.io.masterResp(1) <> lsu.io.rResp

  val memRXbar = Module(
    new Xbar(
      new SramRPortReq(XLen.W),
      new SramRPortResp(32.W),
      Seq(
        // "b00010000_00000000_0000????_????????".BP,
        "b10000000_????????_????????_????0???".BP,
        "b10000000_????????_????????_????1???".BP
      ),
      (req: SramRPortReq) => req.addr,
      (resp: SramRPortResp) => resp.resp
    )
  )
  memRXbar.io.masterReq  <> readArb.io.slaveReq
  memRXbar.io.masterResp <> readArb.io.slaveResp

  val memRPort1 = Module(new SramRPort(XLen.W, 32.W))
  memRPort1.io.req  <> memRXbar.io.slaveReq(0)
  memRPort1.io.resp <> memRXbar.io.slaveResp(0)
  val memRPort2 = Module(new SramRPort(XLen.W, 32.W))
  memRPort2.io.req  <> memRXbar.io.slaveReq(1)
  memRPort2.io.resp <> memRXbar.io.slaveResp(1)

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
