package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class NpcIO extends Bundle {
  val i_inst   = Input(UInt(XLen.W))
  val o_pc     = Output(UInt(XLen.W))
  val o_break  = Output(Bool())
  val o_memLen = Output(UInt())

  val i_op      = Input(UInt(AluOp.getWidth.W))
  val i_dir     = Input(UInt(AluDir.getWidth.W))
  val i_s1      = Input(UInt(XLen.W))
  val i_s2      = Input(UInt(XLen.W))
  val o_d       = Output(UInt(XLen.W))
}

class Npc extends Module {
  val io = IO(new NpcIO)

  val idu = Module(new Idu)
  idu.io.i_instr := io.i_inst
  io.o_break     := idu.io.o_break
  io.o_memLen    := idu.io.o_memLen

  val alu = Module(new Alu)
  alu.io.i_op  := io.i_op
  alu.io.i_dir := io.i_dir
  alu.io.i_s1  := io.i_s1
  alu.io.i_s2  := io.i_s2
  io.o_d       := alu.io.o_d

  val w_update_pc = Wire(Bool())
  val w_dnpc      = Wire(UInt(XLen.W))
  val reg_pc      = RegEnable(w_dnpc, npc.InitPCVal, w_update_pc)

  w_update_pc := true.B
  w_dnpc      := reg_pc + 4.U

  io.o_pc := reg_pc
}
