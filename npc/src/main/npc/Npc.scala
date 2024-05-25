package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class NpcIO extends Bundle {
  val instr = Input(UInt(32.W))
  val pc    = Output(UInt(XLen.W))
  val break = Output(Bool())

  val memLen   = Output(UInt())
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

class Npc extends Module {
  val io = IO(new NpcIO)

  val gpr = Module(new GprFile)
  val csr = Module(new CsrFile)
  val idu = Module(new Idu)
  val alu = Module(new Alu)
  val pc  = RegInit(npc.InitPCVal.U(XLen.W))

  val snpc = pc + 4.U

  io.pc        := pc
  idu.io.instr := io.instr
  io.break     := idu.io.break
  io.ra        := gpr.io.ra

  io.memLen := idu.io.memLen

  gpr.io.rs1Idx := idu.io.rs1Idx
  gpr.io.rs2Idx := idu.io.rs2Idx
  val rs1 = gpr.io.rs1
  val rs2 = gpr.io.rs2
  val imm = idu.io.imm

  val srcASelDec = Decoder1H(
    Seq(
      InstrSrcASel.SrcARs1.BP -> 0,
      InstrSrcASel.SrcAPc.BP  -> 1,
      InstrSrcASel.SrcAR0.BP  -> 2
    )
  )
  val srcASel1H = srcASelDec(idu.io.srcASel)
  val srcA = Mux1H(
    Seq(
      srcASel1H(0) -> rs1,
      srcASel1H(1) -> pc,
      srcASel1H(2) -> 0.U,
      srcASel1H(3) -> 0.U
    )
  )

  val srcBSelDec = Decoder1H(
    Seq(
      InstrSrcBSel.SrcBRs2.BP -> 0,
      InstrSrcBSel.SrcBImm.BP -> 1
    )
  )
  val srcBSel1H = srcBSelDec(idu.io.srcBSel)
  val srcB = Mux1H(
    Seq(
      srcBSel1H(0) -> rs2,
      srcBSel1H(1) -> imm,
      srcBSel1H(2) -> 0.U
    )
  )

  alu.io.s1      := srcA
  alu.io.s2      := srcB
  alu.io.calcOp  := idu.io.aluCalcOp
  alu.io.calcDir := idu.io.aluCalcDir
  alu.io.brCond  := idu.io.aluBrCond

  val brInvalid = alu.io.brInvalid
  val brTarget  = Mux(alu.io.brTaken, pc + imm, pc)

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
  io.memRAddr := alu.io.d
  io.memREn   := memAction1H(0) | memAction1H(1)

  val wbSelDec = Decoder1H(
    Seq(
      InstrWbSel.WbAlu.BP  -> 0,
      InstrWbSel.WbSnpc.BP -> 1,
      InstrWbSel.WbMem.BP  -> 2,
      InstrWbSel.WbCsr.BP  -> 3
    )
  )
  val wbSel1H = wbSelDec(idu.io.wbSel)
  val wbData = Mux1H(
    Seq(
      wbSel1H(0) -> alu.io.d,
      wbSel1H(1) -> snpc,
      wbSel1H(2) -> io.memRData, // TODO: Sign extend this!
      wbSel1H(3) -> 0.U,
      wbSel1H(4) -> 0.U
    )
  )

  gpr.io.rdData := wbData
  gpr.io.rdIdx  := idu.io.rdIdx
  gpr.io.wEn    := idu.io.wbEn

  io.memWAddr := wbData
  io.memWData := rs2
  io.memWEn   := memAction1H(2)

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
      pcSel1H(1) -> alu.io.d,
      pcSel1H(2) -> brTarget,
      pcSel1H(3) -> 0.U,
      pcSel1H(4) -> 0.U
    )
  ) + 4.U

  pc := dnpc

  io.inval := srcASel1H(3) | srcBSel1H(2) | memAction1H(4) | wbSel1H(4) | brInvalid | pcSel1H(4)
}
