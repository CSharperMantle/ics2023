package npc

import chisel3._
import chisel3.util._

import common._
import npc._
import device._

class CoreIO extends Bundle {
  val interrupt = Input(Bool())
  val master    = new Axi4MasterPort
  val slave     = Flipped(new Axi4MasterPort)
}

class Core extends Module {
  val io = IO(new CoreIO)

  private val csr = Module(new CsrFile)
  private val gpr = Module(new GprFile)

  private val ifu      = Module(new Ifu)
  private val idu      = Module(new Idu)
  private val exu      = Module(new Exu)
  private val lsu      = Module(new Lsu)
  private val wbu      = Module(new Wbu)
  private val pcUpdate = Module(new PcUpdate)

  private val readArb = Module(
    new GenericArbiter(new MemReadReq(XLen.W), new MemReadResp(32.W), 2)
  )
  readArb.io.masterReq(0)  <> ifu.io.rReq
  readArb.io.masterResp(0) <> ifu.io.rResp
  readArb.io.masterReq(1)  <> lsu.io.rReq
  readArb.io.masterResp(1) <> lsu.io.rResp

  private val memRXbar = Module(
    new Xbar(
      new MemReadReq(XLen.W),
      new MemReadResp(XLen.W),
      Seq(
        Seq(
          "b00000010_00000000_????????_????????".BP // CLINT
        ),
        Seq(
          "b00001111_????????_????????_????????".BP, // SRAM
          "b00010000_00000000_0000????_????????".BP, // UART16550
          "b00100000_00000000_0000????_????????".BP, // MROM
          "b0011????_????????_????????_????????".BP  // Flash
        )
      ),
      (req: MemReadReq) => req.addr,
      (resp: MemReadResp) => resp.rResp
    )
  )
  memRXbar.io.masterReq  <> readArb.io.slaveReq
  memRXbar.io.masterResp <> readArb.io.slaveResp

  private val clint = Module(new Clint)
  clint.io.rReq  <> memRXbar.io.slaveReq(0)
  clint.io.rResp <> memRXbar.io.slaveResp(0)

  private val outRReq  = memRXbar.io.slaveReq(1)
  private val outRResp = memRXbar.io.slaveResp(1)

  outRReq.ready     := io.master.arready
  io.master.arvalid := outRReq.valid
  io.master.araddr  := outRReq.bits.addr
  io.master.arid    := 0.U
  io.master.arlen   := 0.U
  io.master.arsize  := outRReq.bits.size
  io.master.arburst := AxBurst.Incr.U

  io.master.rready    := outRResp.ready
  outRResp.valid      := io.master.rvalid
  outRResp.bits.rResp := io.master.rresp
  outRResp.bits.data  := io.master.rdata
  // rlast
  // rid

  lsu.io.wReq.ready := io.master.awready & io.master.wready
  io.master.awvalid := lsu.io.wReq.valid
  io.master.awaddr  := lsu.io.wReq.bits.wAddr
  io.master.awid    := 0.U
  io.master.awlen   := 0.U
  io.master.awsize  := lsu.io.wReq.bits.wSize
  io.master.awburst := AxBurst.Incr.U

  io.master.wvalid        := lsu.io.wReq.valid
  io.master.wdata         := lsu.io.wReq.bits.wData
  io.master.wstrb         := lsu.io.wReq.bits.wMask(3, 0)
  io.master.wlast         := lsu.io.wReq.valid
  io.master.bready        := lsu.io.wResp.ready
  lsu.io.wResp.valid      := io.master.bvalid
  lsu.io.wResp.bits.bResp := io.master.bresp
  // bid

  StageConnect(idu.io.msgIn, ifu.io.msgOut)
  StageConnect(exu.io.msgIn, idu.io.msgOut)
  gpr.io.read <> exu.io.gprRead
  csr.io.conn <> exu.io.csrConn
  StageConnect(lsu.io.msgIn, exu.io.msgOut)
  StageConnect(wbu.io.msgIn, lsu.io.msgOut)
  gpr.io.write <> wbu.io.gprWrite
  StageConnect(pcUpdate.io.msgIn, wbu.io.msgOut)
  StageConnect(ifu.io.msgIn, pcUpdate.io.msgOut)

  private val retired     = pcUpdate.io.msgOut.valid
  private val instrCycles = RegInit(0.U(8.W))
  instrCycles := Mux(retired, 0.U, instrCycles + 1.U)

  private val dpi = Module(new Dpi)
  dpi.io.retired := retired
  dpi.io.ebreak  := idu.io.break
  dpi.io.bad     := pcUpdate.io.msgOut.bits.inval
  dpi.io.pc      := pcUpdate.io.msgOut.bits.pc
  dpi.io.cycles  := instrCycles
  dpi.io.instr   := ifu.io.msgOut.bits.instr
  dpi.io.a0      := gpr.io.a0

  io.slave := DontCare
}
