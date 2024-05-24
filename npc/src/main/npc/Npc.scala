package npc

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

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

  val srcASelTable = TruthTable(
    Seq(
      InstrSrcASel.SrcARs1.BP -> "b0001".BP,
      InstrSrcASel.SrcAPc.BP  -> "b0010".BP,
      InstrSrcASel.SrcAR0.BP  -> "b0100".BP
    ),
    "b1000".BP
  )
  val srcASel1H = decoder(idu.io.srcASel, srcASelTable)
  val srcA = Mux1H(
    Seq(
      srcASel1H(0) -> rs1,
      srcASel1H(1) -> pc,
      srcASel1H(2) -> 0.U,
      srcASel1H(3) -> 0.U
    )
  )

  val srcBSelTable = TruthTable(
    Seq(
      InstrSrcBSel.SrcBRs2.BP -> "b001".BP,
      InstrSrcBSel.SrcBImm.BP -> "b010".BP
    ),
    "b100".BP
  )
  val srcBSel1H = decoder(idu.io.srcBSel, srcBSelTable)
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

  val memActionTable = TruthTable(
    Seq(
      MemAction.Rd.BP   -> "b00001".BP,
      MemAction.Rdu.BP  -> "b00010".BP,
      MemAction.Wt.BP   -> "b00100".BP,
      MemAction.None.BP -> "b01000".BP
    ),
    "b10000".BP
  )
  val memAction1H = decoder(idu.io.memAction, memActionTable)
  io.memU     := memAction1H(1)
  io.memRAddr := alu.io.d
  io.memREn   := memAction1H(0) | memAction1H(1)

  val wbSelTable = TruthTable(
    Seq(
      InstrWbSel.WbAlu.BP  -> "b00001".BP,
      InstrWbSel.WbSnpc.BP -> "b00010".BP,
      InstrWbSel.WbMem.BP  -> "b00100".BP,
      InstrWbSel.WbCsr.BP  -> "b01000".BP
    ),
    "b10000".BP
  )
  val wbSel1H = decoder(idu.io.wbSel, wbSelTable)
  val wbData = Mux1H(
    Seq(
      wbSel1H(0) -> alu.io.d,
      wbSel1H(1) -> snpc,
      wbSel1H(2) -> io.memRData,
      wbSel1H(3) -> 0.U,
      wbSel1H(4) -> 0.U
    )
  )

  gpr.io.rdData := wbData
  gpr.io.rdIdx  := idu.io.rdIdx
  gpr.io.wEn    := idu.io.wbEn

  io.memWAddr := wbData
  io.memWData := srcB
  io.memWEn   := memAction1H(2)

  val pcSelTable = TruthTable(
    Seq(
      InstrPcSel.PcSnpc.BP -> "b00001".BP,
      InstrPcSel.PcAlu.BP  -> "b00010".BP,
      InstrPcSel.PcBr.BP   -> "b00100".BP,
      InstrPcSel.PcEpc.BP  -> "b01000".BP
    ),
    "b10000".BP
  )
  val pcSel1H = decoder(idu.io.pcSel, pcSelTable)
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
