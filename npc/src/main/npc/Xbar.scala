package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class MemReadReq(width: Width) extends Bundle {
  val addr = UInt(width)
  val size = UInt(AxSize.W)
}

class MemReadResp(width: Width) extends Bundle {
  val data  = UInt(width)
  val rResp = UInt(2.W)
}

class MemWriteReq(addrWidth: Width, dataWidth: Width) extends Bundle {
  val wAddr = UInt(addrWidth)
  val wData = UInt(dataWidth)
  val wMask = UInt(8.W)
  val wSize = UInt(AxSize.W)
}

class MemWriteResp extends Bundle {
  val bResp = UInt(2.W)
}

class GenericArbiter[TReq <: Data, TResp <: Data](
  private val req:  TReq,
  private val resp: TResp,
  n:                Int)
    extends Module {
  class Port extends Bundle {
    val masterReq  = Flipped(Vec(n, Irrevocable(req)))
    val slaveReq   = Irrevocable(req)
    val masterResp = Vec(n, Irrevocable(resp))
    val slaveResp  = Flipped(Irrevocable(resp))
    val chosen     = Output(UInt(log2Ceil(n).W))
  }
  val io = IO(new Port)

  private val transactions = RegInit(VecInit(Seq.fill(n)(0.B)))
  for ((trans, i) <- transactions.zipWithIndex) {
    trans := Mux(io.masterReq(i).valid, 1.B, Mux(io.masterResp(i).ready, 0.B, trans))
  }

  private val arb = Module(new RRArbiter(new Bundle {}, n))
  for (i <- (0 until n)) {
    arb.io.in(i).valid := transactions(i)
  }
  arb.io.out.ready := MuxLookup(arb.io.chosen, 0.B)(
    (0 until n).map(i => i.asUInt -> io.masterResp(i).ready)
  )

  private object State extends CvtChiselEnum {
    val S_Idle  = Value
    val S_Trans = Value
  }
  import State._
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle  -> Mux(arb.io.out.valid, S_Trans, S_Idle),
      S_Trans -> Mux(io.slaveResp.ready, S_Idle, S_Trans)
    )
  )
  private val inTrans = y === S_Trans

  // Wire requests and responses
  io.slaveReq.valid := MuxLookup(arb.io.chosen, 0.B)(
    (0 until n).map(i => i.asUInt -> (inTrans & io.masterReq(i).valid))
  )
  io.slaveReq.bits := MuxLookup(arb.io.chosen, io.masterReq(0).bits)(
    (0 until n).map(i => i.asUInt -> io.masterReq(i).bits)
  )
  for ((req, i) <- io.masterReq.zipWithIndex) {
    req.ready := (arb.io.chosen === i.asUInt) & io.slaveReq.ready
  }
  for ((resp, i) <- io.masterResp.zipWithIndex) {
    resp.valid := (arb.io.chosen === i.asUInt) & io.slaveResp.valid
    resp.bits  := io.slaveResp.bits
  }
  io.slaveResp.ready := MuxLookup(arb.io.chosen, 0.B)(
    (0 until n).map(i => i.asUInt -> io.masterResp(i).ready)
  )

  io.chosen := arb.io.chosen
}

class Xbar[TReq <: Data, TResp <: Data](
  private val req:      TReq,
  private val resp:     TResp,
  private val addrPats: Seq[Iterable[BitPat]],
  private val selAddr:  TReq => UInt,
  private val selResp:  TResp => UInt)
    extends Module {
  val n = addrPats.length

  class XbarIO extends Bundle {
    val masterReq  = Flipped(Irrevocable(req))
    val masterResp = Irrevocable(resp)
    val slaveReq   = Vec(n, Irrevocable(req))
    val slaveResp  = Flipped(Vec(n, Irrevocable(resp)))
  }
  val io = IO(new XbarIO)

  private val addr = RegInit(0.U)

  addr := Mux(io.masterReq.valid, selAddr(io.masterReq.bits), Mux(io.masterResp.ready, 0.U, addr))

  private val addrSelDec = MultiDecoder1H(addrPats.zipWithIndex)
  private val addrSel1H  = addrSelDec(addr)
  private val addrBad    = addrSel1H(addrSelDec.bitBad)

  private object State extends CvtChiselEnum {
    val S_Idle  = Value
    val S_Trans = Value
  }
  import State._
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle  -> Mux(io.masterReq.valid, S_Trans, S_Idle),
      S_Trans -> Mux(io.masterResp.ready, S_Idle, S_Trans)
    )
  )
  private val inTrans = y === S_Trans

  for ((slave, i) <- io.slaveReq.zipWithIndex) {
    slave.valid := Mux(addrSel1H(i), io.masterReq.valid, 0.B)
    slave.bits  := io.masterReq.bits
  }
  io.masterReq.ready := Mux(
    inTrans,
    Mux1H(
      (0 until n).map(i => addrSel1H(i) -> io.slaveReq(i).ready) ++ Seq(
        addrBad -> io.masterReq.valid
      )
    ),
    0.B
  )

  io.masterResp.valid := Mux(
    inTrans,
    Mux1H(
      (0 until n).map(i => addrSel1H(i) -> io.slaveResp(i).valid) ++ Seq(
        addrBad -> 1.B
      )
    ),
    0.B
  )
  io.masterResp.bits := Mux1H(
    (0 until n).map(i => addrSel1H(i) -> io.slaveResp(i).bits) ++ Seq(
      addrBad -> io.slaveResp(0).bits
    )
  )
  selResp(io.masterResp.bits) := Mux1H(
    (0 until n).map(i => addrSel1H(i) -> selResp(io.slaveResp(i).bits)) ++ Seq(
      addrBad -> BResp.DecErr.U
    )
  )
  for ((slave, i) <- io.slaveResp.zipWithIndex) {
    slave.ready := Mux(addrSel1H(i), io.masterResp.ready, 0.B)
  }
}
