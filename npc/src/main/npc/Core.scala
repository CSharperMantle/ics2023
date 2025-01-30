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

  private val icache   = Module(new Cache(16))
  private val ifu      = Module(new Ifu)
  private val idu      = Module(new Idu)
  private val exu      = Module(new Exu)
  private val lsu      = Module(new Lsu)
  private val wbu      = Module(new Wbu)
  private val pcUpdate = Module(new PcUpdate)

  icache.io.req   <> ifu.io.rReq
  icache.io.resp  <> ifu.io.rResp
  icache.io.flush := idu.io.cacheFlush

  private val readArb = Module(
    new GenericArbiter(new MemReadReq(32.W), new MemReadResp(32.W), 2)
  )
  readArb.io.masterReq(0)  <> icache.io.memReq
  readArb.io.masterResp(0) <> icache.io.memResp
  readArb.io.masterReq(1)  <> lsu.io.rReq
  readArb.io.masterResp(1) <> lsu.io.rResp

  private val memRXbar = Module(
    new Xbar(
      new MemReadReq(32.W),
      new MemReadResp(32.W),
      Seq(
        Seq(
          "b00000010_00000000_????????_????????".BP // CLINT
        ),
        Seq(
          "b00001111_????????_????????_????????".BP, // SRAM
          "b00010000_00000000_0000????_????????".BP, // UART16550
          "b00010000_00000000_0001????_????????".BP, // SPI master
          "b00010000_00000000_00100000_0000????".BP, // GPIO
          "b00010000_00000001_00010000_00000???".BP, // PS/2 keyboard
          "b00100000_00000000_0000????_????????".BP, // MROM
          "b00100001_000?????_????????_????????".BP, // VGA
          "b0011????_????????_????????_????????".BP, // Flash
          "b100?????_????????_????????_????????".BP, // PSRAM
          "b101?????_????????_????????_????????".BP  // SDRAM
          // "b1100????_????????_????????_????????".BP  // ChipLink slave
        )
      ),
      (req: MemReadReq) => req.addr,
      (resp: MemReadResp) => resp.rResp,
      RResp.DecErr
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
  io.master.arburst := AxBurst.Fixed.U

  io.master.rready    := outRResp.ready
  outRResp.valid      := io.master.rvalid
  outRResp.bits.rResp := RResp(io.master.rresp)
  outRResp.bits.data  := io.master.rdata
  // rlast
  // rid

  private val memWXbar = Module(
    new Xbar(
      new MemWriteReq(32.W, 32.W),
      new MemWriteResp,
      Seq(
        Seq(
          "b00001111_????????_????????_????????".BP, // SRAM
          "b00010000_00000000_0000????_????????".BP, // UART16550
          "b00010000_00000000_0001????_????????".BP, // SPI master
          "b00010000_00000000_00100000_0000????".BP, // GPIO
          "b00100001_000?????_????????_????????".BP, // VGA
          "b100?????_????????_????????_????????".BP, // PSRAM
          "b101?????_????????_????????_????????".BP  // SDRAM
          // "b1100????_????????_????????_????????".BP  // ChipLink slave
        )
      ),
      (req: MemWriteReq) => req.wAddr,
      (resp: MemWriteResp) => resp.bResp,
      BResp.DecErr
    )
  )
  memWXbar.io.masterReq  <> lsu.io.wReq
  memWXbar.io.masterResp <> lsu.io.wResp

  memWXbar.io.slaveReq(0).ready := io.master.awready & io.master.wready
  io.master.awvalid             := memWXbar.io.slaveReq(0).valid
  io.master.awaddr              := memWXbar.io.slaveReq(0).bits.wAddr
  io.master.awid                := 0.U
  io.master.awlen               := 0.U
  io.master.awsize              := memWXbar.io.slaveReq(0).bits.wSize
  io.master.awburst             := AxBurst.Fixed.U

  io.master.wvalid                    := memWXbar.io.slaveReq(0).valid
  io.master.wdata                     := memWXbar.io.slaveReq(0).bits.wData
  io.master.wstrb                     := memWXbar.io.slaveReq(0).bits.wMask(3, 0)
  io.master.wlast                     := memWXbar.io.slaveReq(0).valid
  io.master.bready                    := memWXbar.io.slaveResp(0).ready
  memWXbar.io.slaveResp(0).valid      := io.master.bvalid
  memWXbar.io.slaveResp(0).bits.bResp := BResp(io.master.bresp)
  // bid

  StageConnect(ifu.io.msgOut, idu.io.msgIn, idu.io.msgOut)
  StageConnect(idu.io.msgOut, exu.io.msgIn, exu.io.msgOut)
  StageConnect(exu.io.msgOut, lsu.io.msgIn, lsu.io.msgOut)
  StageConnect(lsu.io.msgOut, wbu.io.msgIn, wbu.io.msgOut)
  StageConnect(wbu.io.msgOut, pcUpdate.io.msgIn, pcUpdate.io.msgOut)
  StageConnect(pcUpdate.io.msgOut, ifu.io.msgIn, ifu.io.msgOut)
  gpr.io.read  <> exu.io.gprRead
  csr.io.conn  <> exu.io.csrConn
  gpr.io.write <> wbu.io.gprWrite

  private val dpi = Module(new Dpi)
  dpi.io.retired     := pcUpdate.io.msgOut.valid
  dpi.io.pc          := pcUpdate.io.msgOut.bits.pc
  dpi.io.ebreak      := idu.io.break
  dpi.io.instr       := ifu.io.msgOut.bits.instr
  dpi.io.memEn       := idu.io.msgOut.bits.memAction =/= MemAction.MemNone
  dpi.io.rwAddr      := exu.io.msgOut.bits.d
  dpi.io.bad         := pcUpdate.io.msgOut.bits.bad
  dpi.io.ifuInValid  := ifu.io.instrStale
  dpi.io.iduInValid  := idu.io.msgIn.valid
  dpi.io.exuInValid  := exu.io.msgIn.valid
  dpi.io.lsuInValid  := lsu.io.msgIn.valid
  dpi.io.wbuInValid  := wbu.io.msgIn.valid
  dpi.io.ifuOutValid := ifu.io.msgOut.valid
  dpi.io.iduOutValid := idu.io.msgOut.valid
  dpi.io.exuOutValid := exu.io.msgOut.valid
  dpi.io.lsuOutValid := lsu.io.msgOut.valid
  dpi.io.wbuOutValid := wbu.io.msgOut.valid
  dpi.io.icacheHit   := icache.io.hit
  dpi.io.icacheMiss  := icache.io.miss

  io.slave := DontCare
}
