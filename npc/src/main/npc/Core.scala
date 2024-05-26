package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class CoreIO extends Bundle {
  val pc    = Output(UInt(XLen.W))
  val break = Output(Bool())
  val inval = Output(Bool())
  val a0    = Output(UInt(XLen.W))
}

class Core extends Module {
  val io = IO(new CoreIO)

  val csr = Module(new CsrFile)
  val gpr = Module(new GprFile)

  val ifu  = Module(new Ifu)
  val idu  = Module(new Idu)
  val exu  = Module(new Exu)
  val memu = Module(new Memu)
  val wbu  = Module(new Wbu)

  val pc = RegInit(InitPCVal.U(XLen.W))

  val snpc = pc + 4.U

  io.pc    := pc
  io.break := idu.io.break
  io.a0    := gpr.io.a0

  ifu.io.pc    := pc
  idu.io.instr := ifu.io.instr

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
  exu.io.rs2     := gpr.io.rs2
  exu.io.pc      := pc
  exu.io.imm     := imm

  csr.io.s1     := exu.io.csrS1
  csr.io.csrIdx := imm(11, 0)
  csr.io.csrOp  := idu.io.csrOp

  memu.io.memAction := idu.io.memAction
  memu.io.memWidth  := idu.io.memWidth
  memu.io.memRAddr  := exu.io.d
  memu.io.memWAddr  := exu.io.d
  memu.io.memWData  := gpr.io.rs2

  wbu.io.wbSel    := idu.io.wbSel
  wbu.io.dataAlu  := exu.io.d
  wbu.io.dataSnpc := snpc
  wbu.io.dataMem  := memu.io.memRData
  wbu.io.dataCsr  := csr.io.csrVal

  gpr.io.rdData := wbu.io.d
  gpr.io.rdIdx  := idu.io.rdIdx
  gpr.io.wEn    := idu.io.wbEn

  val brTarget = Mux(exu.io.brTaken, pc + imm, snpc)
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
      pcSel1H(0) -> snpc,
      pcSel1H(1) -> exu.io.d,
      pcSel1H(2) -> brTarget,
      pcSel1H(3) -> csr.io.epc,
      pcSel1H(4) -> 0.U
    )
  )

  pc := dnpc

  io.inval := ~reset.asBool & (exu.io.inval | memu.io.inval | wbu.io.inval | pcSel1H(pcSelDec.bitBad))
}
