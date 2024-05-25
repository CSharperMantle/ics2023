package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class CoreIO extends Bundle {
  val instr = Input(UInt(32.W))
  val pc    = Output(UInt(XLen.W))
  val break = Output(Bool())

  val memWidth = Output(UInt())
  val memU     = Output(Bool())
  val memREn   = Output(Bool())
  val memRAddr = Output(UInt(XLen.W))
  val memRData = Input(UInt(XLen.W))
  val memWEn   = Output(Bool())
  val memWAddr = Output(UInt(XLen.W))
  val memWData = Output(UInt(XLen.W))

  val inval = Output(Bool())
  val ra    = Output(UInt(XLen.W))
}

class Core extends Module {
  val io = IO(new CoreIO)

  val csr  = Module(new CsrFile)
  val gpr  = Module(new GprFile)
  val idu  = Module(new Idu)
  val exu  = Module(new Exu)
  val sext = Module(new SExtender)
  val wbu  = Module(new Wbu)
  val pc   = RegInit(InitPCVal.U(XLen.W))

  val snpc = pc + 4.U

  io.pc        := pc
  idu.io.instr := io.instr
  io.break     := idu.io.break
  io.ra        := gpr.io.ra

  io.memWidth := idu.io.memWidth

  gpr.io.rs1Idx := idu.io.rs1Idx
  gpr.io.rs2Idx := idu.io.rs2Idx
  val rs2 = gpr.io.rs2
  val imm = idu.io.imm

  exu.io.srcASel := idu.io.srcASel
  exu.io.srcBSel := idu.io.srcBSel
  exu.io.calcOp  := idu.io.aluCalcOp
  exu.io.calcDir := idu.io.aluCalcDir
  exu.io.brCond  := idu.io.aluBrCond
  exu.io.rs1Idx  := idu.io.rs1Idx
  exu.io.rs1     := gpr.io.rs1
  exu.io.rs2     := rs2
  exu.io.pc      := pc
  exu.io.imm     := imm

  csr.io.s1     := exu.io.csrS1
  csr.io.csrIdx := imm(11, 0)
  csr.io.csrOp  := idu.io.csrOp

  val memActionDec = Decoder1H(
    Seq(
      MemAction.Rd.BP   -> 0,
      MemAction.Rdu.BP  -> 1,
      MemAction.Wt.BP   -> 2,
      MemAction.None.BP -> 3
    )
  )
  val memAction1H = memActionDec(idu.io.memAction)
  io.memU     := memAction1H(1)
  io.memRAddr := exu.io.d
  io.memREn   := memAction1H(0) | memAction1H(1)

  sext.io.sextData := io.memRData
  sext.io.sextW    := idu.io.memWidth

  wbu.io.wbSel    := idu.io.wbSel
  wbu.io.dataAlu  := exu.io.d
  wbu.io.dataSnpc := snpc
  wbu.io.dataMem  := sext.io.sextRes
  wbu.io.dataCsr  := csr.io.csrVal

  gpr.io.rdData := wbu.io.d
  gpr.io.rdIdx  := idu.io.rdIdx
  gpr.io.wEn    := idu.io.wbEn

  io.memWAddr := wbu.io.d
  io.memWData := rs2
  io.memWEn   := memAction1H(2)

  val brTarget = Mux(exu.io.brTaken, pc + imm, pc)
  val pcSelDec = Decoder1H(
    Seq(
      InstrPcSel.PcSnpc.BP -> 0,
      InstrPcSel.PcAlu.BP  -> 1,
      InstrPcSel.PcBr.BP   -> 2,
      InstrPcSel.PcEpc.BP  -> 3
    )
  )
  val pcSel1H = pcSelDec(idu.io.pcSel)
  val dnpc = Mux1H(
    Seq(
      pcSel1H(0) -> pc,
      pcSel1H(1) -> exu.io.d,
      pcSel1H(2) -> brTarget,
      pcSel1H(3) -> csr.io.epc,
      pcSel1H(4) -> 0.U
    )
  ) + 4.U

  pc := dnpc

  io.inval := memAction1H(memActionDec.bitBad) | exu.io.inval | wbu.io.inval | pcSel1H(memActionDec.bitBad)
}
