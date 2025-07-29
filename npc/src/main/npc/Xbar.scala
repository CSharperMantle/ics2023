package npc

import chisel3._
import chisel3.experimental.requireIsChiselType
import chisel3.util._

import common._
import npc._

class MemReadReq(width: Width) extends Bundle {
  val addr = UInt(width)
  val size = UInt(AxSize.W)
}

class MemReadResp(width: Width) extends Bundle {
  val data  = UInt(width)
  val rResp = RResp()
}

class MemWriteReq(addrWidth: Width, dataWidth: Width) extends Bundle {
  val wAddr = UInt(addrWidth)
  val wData = UInt(dataWidth)
  val wMask = UInt(8.W)
  val wSize = UInt(AxSize.W)
}

class MemWriteResp extends Bundle {
  val bResp = BResp()
}

class RRArbiter(n: Int) extends Module {
  class Port extends Bundle {
    val request = Input(Vec(n, Bool()))
    val done    = Input(Vec(n, Bool()))
    val choice  = Output(Valid(UInt(log2Up(n).W)))
  }
  val io = IO(new Port)

  private val current = RegInit(0.U(log2Up(n).W))
  private val next    = Mux(current === (n.U - 1.U), 0.U, current + 1.U)

  current := MuxCase(
    current,
    Seq(
      (!io.request(current))                     -> next,
      (io.request(current) && !io.done(current)) -> current,
      (io.request(current) && io.done(current))  -> next
    )
  )

  io.choice.valid := io.request(current)
  io.choice.bits  := current
}

class GenericArbiter[TReq <: Data, TResp <: Data](
  reqGen:  TReq,
  respGen: TResp,
  n:       Int)
    extends Module {
  requireIsChiselType(reqGen)
  requireIsChiselType(respGen)

  class Port extends Bundle {
    val masterReq  = Flipped(Vec(n, Decoupled(reqGen)))
    val slaveReq   = Decoupled(reqGen)
    val masterResp = Vec(n, Decoupled(respGen))
    val slaveResp  = Flipped(Decoupled(respGen))
    val choice     = Output(UInt(log2Up(n).W))
  }
  val io = IO(new Port)

  private val transactions = RegInit(VecInit(Seq.fill(n)(false.B)))
  for ((trans, i) <- transactions.zipWithIndex) {
    trans := Mux(io.masterReq(i).valid, true.B, Mux(io.masterResp(i).fire, false.B, trans))
  }

  private val arb = Module(new RRArbiter(n))
  for (i <- (0 until n)) {
    arb.io.request(i) := transactions(i)
    arb.io.done(i)    := io.masterResp(i).fire
  }

  // Wire requests and responses
  io.slaveReq.valid := arb.io.choice.valid && MuxLookup(arb.io.choice.bits, false.B)(
    (0 until n).map(i => i.asUInt -> io.masterReq(i).valid)
  )
  io.slaveReq.bits := MuxLookup(arb.io.choice.bits, io.masterReq(0).bits)(
    (0 until n).map(i => i.asUInt -> io.masterReq(i).bits)
  )
  for ((req, i) <- io.masterReq.zipWithIndex) {
    req.ready := arb.io.choice.valid && (arb.io.choice.bits === i.asUInt) && io.slaveReq.ready
  }
  for ((resp, i) <- io.masterResp.zipWithIndex) {
    resp.valid := arb.io.choice.valid && (arb.io.choice.bits === i.asUInt) && io.slaveResp.valid
    resp.bits  := io.slaveResp.bits
  }
  io.slaveResp.ready := arb.io.choice.valid && MuxLookup(arb.io.choice.bits, false.B)(
    (0 until n).map(i => i.asUInt -> io.masterResp(i).ready)
  )

  io.choice := arb.io.choice.bits
}

class Xbar[TReqBundle <: Data, TRespBundle <: Data, TResp <: Data](
  private val req:          TReqBundle,
  private val resp:         TRespBundle,
  private val addrPats:     Seq[Iterable[BitPat]],
  private val selAddr:      TReqBundle => UInt,
  private val selResp:      TRespBundle => TResp,
  private val errorRespVal: TResp)
    extends Module {
  val n = addrPats.length

  class XbarIO extends Bundle {
    val masterReq  = Flipped(Irrevocable(req))
    val masterResp = Irrevocable(resp)
    val slaveReq   = Vec(n, Irrevocable(req))
    val slaveResp  = Flipped(Vec(n, Irrevocable(resp)))
  }
  val io = IO(new XbarIO)

  private val inTrans = RegInit(false.B)
  inTrans := Mux(io.masterReq.valid, true.B, Mux(io.masterResp.ready, false.B, inTrans))

  private val addr = RegEnable(selAddr(io.masterReq.bits), 0.U, io.masterReq.valid)

  private val addrSelDec = MultiDecoder1H(addrPats.zipWithIndex)
  private val addrSel1H  = addrSelDec(Mux(io.masterReq.valid, selAddr(io.masterReq.bits), addr))
  private val addrBad    = addrSel1H(addrSelDec.bitBad)

  for ((slave, i) <- io.slaveReq.zipWithIndex) {
    slave.valid := Mux(inTrans & addrSel1H(i), io.masterReq.valid, false.B)
    slave.bits  := io.masterReq.bits
  }
  io.masterReq.ready := Mux(
    inTrans,
    Mux1H(
      (0 until n).map(i => addrSel1H(i) -> io.slaveReq(i).ready) ++ Seq(
        addrBad -> io.masterReq.valid
      )
    ),
    false.B
  )

  io.masterResp.valid := Mux(
    inTrans,
    Mux1H(
      (0 until n).map(i => addrSel1H(i) -> io.slaveResp(i).valid) ++ Seq(
        addrBad -> 1.B
      )
    ),
    false.B
  )
  io.masterResp.bits := Mux1H(
    (0 until n).map(i => addrSel1H(i) -> io.slaveResp(i).bits) ++ Seq(
      addrBad -> io.slaveResp(0).bits
    )
  )
  selResp(io.masterResp.bits) := Mux1H(
    (0 until n).map(i => addrSel1H(i) -> selResp(io.slaveResp(i).bits)) ++ Seq(
      addrBad -> errorRespVal
    )
  )
  for ((slave, i) <- io.slaveResp.zipWithIndex) {
    slave.ready := Mux(addrSel1H(i), io.masterResp.ready, 0.B)
  }
}
