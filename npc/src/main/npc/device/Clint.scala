package npc.device

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import _root_.npc._
import common._
import npc._

class Clint extends Module {
  class Port extends Bundle {
    val rReq  = Flipped(Irrevocable(new MemReadReq(32.W)))
    val rResp = Irrevocable(new MemReadResp(32.W))
  }
  val io = IO(new Port)

  private object Regs extends CvtChiselEnum {
    val MsipIdx     = Value
    val MtimecmpIdx = Value
    val MtimeIdx    = Value
    val UnkIdx      = Value
  }
  import Regs._

  private val regs = Mem(Regs.all.length + 1, UInt(64.W))

  private val regDecTable = TruthTable(
    Seq(
      "b00000010_00000000_00000000_00000000".BP -> MsipIdx.BP,
      "b00000010_00000000_01000000_00000?00".BP -> MtimecmpIdx.BP,
      "b00000010_00000000_10111111_11111?00".BP -> MtimeIdx.BP
    ),
    UnkIdx.BP
  )
  private val rRegIdx = decoder(io.rReq.bits.addr, regDecTable)

  private val rAddr = RegEnable(io.rReq.bits.addr, 0.U, io.rReq.valid)

  private object State extends CvtChiselEnum {
    val S_Idle      = Value
    val S_WaitReady = Value
  }
  import State._
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle      -> Mux(io.rReq.valid, S_WaitReady, S_Idle),
      S_WaitReady -> Mux(io.rResp.ready, S_Idle, S_WaitReady)
    )
  )

  io.rReq.ready := io.rReq.valid

  io.rResp.bits.data  := Mux(rAddr(2), regs(rRegIdx)(63, 32), regs(rRegIdx)(31, 0))
  io.rResp.bits.rResp := Mux(rRegIdx === UnkIdx.U, RResp.DecErr.U, RResp.Okay.U)
  io.rResp.valid      := y === S_WaitReady

  regs(MtimeIdx.U) := regs(MtimeIdx.U) + 1.U
}
