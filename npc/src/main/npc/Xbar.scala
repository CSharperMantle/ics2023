package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class GenericArbiterIO[TReq <: Data, TResp <: Data](
  private val req:  TReq,
  private val resp: TResp,
  n:                Int)
    extends Bundle {
  val masterReq  = Flipped(Vec(n, Irrevocable(req)))
  val slaveReq   = Irrevocable(req)
  val masterResp = Vec(n, Irrevocable(resp))
  val slaveResp  = Flipped(Irrevocable(resp))
  val chosen     = Output(UInt(log2Ceil(n).W))
}

class GenericArbiter[TReq <: Data, TResp <: Data](
  private val req:  TReq,
  private val resp: TResp,
  n:                Int)
    extends Module {
  val io = IO(new GenericArbiterIO(req, resp, n))

  private val arbiter = Module(new RRArbiter(new Bundle {}, n))

  private val transactions = RegInit(VecInit(Seq.fill(n)(0.B)))
  for ((trans, i) <- transactions.zipWithIndex) {
    trans := Mux(io.masterReq(i).valid, 1.B, Mux(io.masterResp(i).ready, 0.B, trans))
  }

  private object State extends CvtChiselEnum {
    val S_Idle  = Value
    val S_Trans = Value
  }
  import State._
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle  -> Mux(arbiter.io.out.valid, S_Trans, S_Idle),
      S_Trans -> Mux(io.slaveResp.ready, S_Idle, S_Trans)
    )
  )
  private val inTrans = y === S_Trans

  // Connect arbiter states
  for (((arbIn, req), i) <- arbiter.io.in.zip(transactions).zipWithIndex) {
    arbIn.valid := req
  }
  arbiter.io.out.ready := MuxLookup(arbiter.io.chosen, 0.B)(
    (0 until n).map(i => i.asUInt -> io.masterResp(i).ready)
  )

  // Wire requests and responses
  io.slaveReq.valid := MuxLookup(arbiter.io.chosen, 0.B)(
    (0 until n).map(i => i.asUInt -> (inTrans & io.masterReq(i).valid))
  )
  io.slaveReq.bits := MuxLookup(arbiter.io.chosen, io.masterReq(0).bits)(
    (0 until n).map(i => i.asUInt -> io.masterReq(i).bits)
  )
  for ((req, i) <- io.masterReq.zipWithIndex) {
    req.ready := (arbiter.io.chosen === i.asUInt) & io.slaveReq.ready
  }
  for ((resp, i) <- io.masterResp.zipWithIndex) {
    resp.valid := (arbiter.io.chosen === i.asUInt) & io.slaveResp.valid
    resp.bits  := io.slaveResp.bits
  }
  io.slaveResp.ready := MuxLookup(arbiter.io.chosen, 0.B)(
    (0 until n).map(i => i.asUInt -> io.masterResp(i).ready)
  )

  io.chosen := arbiter.io.chosen
}

class XbarIO[TReq <: Data, TResp <: Data](
  private val req:  TReq,
  private val resp: TResp,
  n:                Int)
    extends Bundle {
  val masterReq  = Flipped(Irrevocable(req))
  val masterResp = Irrevocable(resp)
  val slaveReq   = Vec(n, Irrevocable(req))
  val slaveResp  = Flipped(Vec(n, Irrevocable(resp)))
}

class Xbar[TReq <: Data, TResp <: Data](
  private val req:      TReq,
  private val resp:     TResp,
  private val addrPats: Seq[BitPat],
  private val selAddr:  TReq => UInt,
  private val selResp:  TResp => UInt)
    extends Module {
  val n  = addrPats.length
  val io = IO(new XbarIO(req, resp, n))

  private val addr = RegInit(0.U)

  addr := Mux(io.masterReq.valid, selAddr(io.masterReq.bits), Mux(io.masterResp.ready, 0.U, addr))

  private val addrSelDec = Decoder1H(addrPats.zipWithIndex)
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
        addrSel1H(addrSelDec.bitBad) -> io.masterReq.valid
      )
    ),
    0.B
  )

  io.masterResp.valid := Mux(
    inTrans,
    Mux1H(
      (0 until n).map(i => addrSel1H(i) -> io.slaveResp(i).valid) ++ Seq(
        addrSel1H(addrSelDec.bitBad) -> 1.B
      )
    ),
    0.B
  )
  io.masterResp.bits := Mux1H(
    (0 until n).map(i => addrSel1H(i) -> io.slaveResp(i).bits) ++ Seq(
      addrSel1H(addrSelDec.bitBad) -> io.slaveResp(0).bits
    )
  )
  selResp(io.masterResp.bits) := Mux1H(
    (0 until n).map(i => addrSel1H(i) -> selResp(io.slaveResp(i).bits)) ++ Seq(
      addrSel1H(addrSelDec.bitBad) -> 3.U(2.W)
    )
  )
  for ((slave, i) <- io.slaveResp.zipWithIndex) {
    slave.ready := Mux(addrSel1H(i), io.masterResp.ready, 0.B)
  }
}
