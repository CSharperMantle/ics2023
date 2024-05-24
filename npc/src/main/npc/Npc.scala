package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class NpcIO extends Bundle {
  val inst   = Input(UInt(XLen.W))
  val pc     = Output(UInt(XLen.W))
  val break  = Output(Bool())
  val memLen = Output(UInt())

  val op  = Input(UInt(AluOp.getWidth.W))
  val dir = Input(UInt(AluDir.getWidth.W))
  val s1  = Input(UInt(XLen.W))
  val s2  = Input(UInt(XLen.W))
  val d   = Output(UInt(XLen.W))
}

class Npc extends Module {
  val io = IO(new NpcIO)

  val idu = Module(new Idu)
  idu.io.instr := io.inst
  io.break     := idu.io.break
  io.memLen    := idu.io.memLen

  val alu = Module(new Alu)
  alu.io.op  := io.op
  alu.io.dir := io.dir
  alu.io.s1  := io.s1
  alu.io.s2  := io.s2
  io.d       := alu.io.d

  val w_update_pc = Wire(Bool())
  val w_dnpc      = Wire(UInt(XLen.W))
  val reg_pc      = RegEnable(w_dnpc, npc.InitPCVal, w_update_pc)

  w_update_pc := true.B
  w_dnpc      := reg_pc + 4.U

  io.pc := reg_pc
}
