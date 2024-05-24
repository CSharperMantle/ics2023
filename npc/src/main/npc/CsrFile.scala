package npc

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import common._
import npc._

object CsrOp extends CvtChiselEnum {
  val Rw = Value
  val Rs = Value
  val Rc = Value
  val Unk = Value
}

class CsrFileIO extends Bundle {
}

class CsrFile extends Module {
  val io = IO(new CsrFileIO)
}
