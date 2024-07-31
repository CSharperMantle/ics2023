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

  // Connect arbiter states
  for ((arbIn, req) <- arbiter.io.in.zip(transactions)) {
    arbIn.valid := req
  }
  arbiter.io.out.ready := MuxLookup(arbiter.io.chosen, 0.B)(
    (0 until n).map(i => i.asUInt -> io.masterResp(i).ready)
  )

  // Wire requests and responses
  io.slaveReq.valid := arbiter.io.out.valid
  io.slaveReq.bits := MuxLookup(arbiter.io.chosen, io.masterReq(0).bits)(
    (0 until n).map(i => i.asUInt -> io.masterReq(i).bits)
  )
  for ((req, i) <- io.masterReq.zipWithIndex) {
    req.ready := (arbiter.io.chosen === i.asUInt) & io.slaveReq.ready
  }
  for ((resp, i) <- io.masterResp.zipWithIndex) {
    resp.bits  := io.slaveResp.bits
    resp.valid := (arbiter.io.chosen === i.asUInt) & io.slaveResp.valid
  }
  io.slaveResp.ready := MuxLookup(arbiter.io.chosen, 0.B)(
    (0 until n).map(i => i.asUInt -> io.masterResp(i).ready)
  )

  io.chosen := arbiter.io.chosen
}
