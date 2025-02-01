package npc

import chisel3._
import chisel3.util._

import common._
import npc._

class CacheLine(val addrWidth: Int, val indexWidth: Int, val offsetWidth: Int) extends Bundle {
  val tagWidth = addrWidth - indexWidth - offsetWidth

  val valid = Bool()
  val tag   = UInt(tagWidth.W)
  val data  = UInt((8 * BigInt(2).pow(offsetWidth)).toInt.W)
  val rResp = RResp()
}

class Cache(val numLines: Int) extends Module {
  require(isPow2(numLines))

  class Port extends Bundle {
    val req     = Flipped(Irrevocable(new MemReadReq(XLen.W)))
    val resp    = Irrevocable(new MemReadResp(32.W))
    val memReq  = Irrevocable(new MemReadReq(XLen.W))
    val memResp = Flipped(Irrevocable(new MemReadResp(32.W)))
    val flush   = Input(Bool())
    val hit     = Output(Bool())
    val miss    = Output(Bool())
  }
  val io = IO(new Port)

  private val lines = SRAM(numLines, new CacheLine(XLen, log2Up(numLines), 2), 0, 0, 1)

  private val lineAddr = Wire(UInt(lines.dataType.indexWidth.W))

  private val lineStale = RegInit(VecInit(Seq.fill(numLines)(false.B)))

  private val lineWire  = Wire(lines.dataType)
  private val lineValid = Wire(Bool())

  private val tag = io.req.bits.addr(XLen - 1, XLen - lines.dataType.tagWidth)

  object State extends CvtChiselEnum {
    val S_Idle         = Value
    val S_Query        = Value
    val S_Compare      = Value
    val S_HitReply     = Value
    val S_MissReq      = Value
    val S_MissWaitResp = Value
    val S_MissReply    = Value
  }
  import State._
  private val y = RegInit(S_Idle)
  y := MuxLookup(y, S_Idle)(
    Seq(
      S_Idle         -> Mux(io.req.valid, S_Query, S_Idle),
      S_Query        -> S_Compare,
      S_Compare      -> Mux(lineValid, S_HitReply, S_MissReq),
      S_HitReply     -> Mux(io.resp.ready, S_Idle, S_HitReply),
      S_MissReq      -> Mux(io.memReq.ready, S_MissWaitResp, S_MissReq),
      S_MissWaitResp -> Mux(io.memResp.valid, S_MissReply, S_MissWaitResp),
      S_MissReply    -> Mux(io.resp.ready, S_Idle, S_MissReply)
    )
  )

  lineAddr := io.req.bits.addr(
    lines.dataType.indexWidth + lines.dataType.offsetWidth - 1,
    lines.dataType.offsetWidth
  )

  private val replaceLine = Wire(lines.dataType)
  replaceLine.valid := true.B
  replaceLine.tag   := tag
  replaceLine.data  := io.memResp.bits.data
  replaceLine.rResp := io.memResp.bits.rResp

  private val line = RegInit(0.U.asTypeOf(lines.dataType))
  line := MuxCase(
    line,
    Seq(
      (y === S_Query)  -> lines.readwritePorts(0).readData,
      io.memResp.valid -> replaceLine
    )
  )

  lineWire  := line
  lineValid := ~(io.flush | lineStale(lineAddr)) & line.valid & line.tag === tag

  lines.readwritePorts(0).enable    := io.req.valid | io.memResp.valid
  lines.readwritePorts(0).isWrite   := io.memResp.valid
  lines.readwritePorts(0).writeData := replaceLine
  lines.readwritePorts(0).address   := lineAddr

  for ((e, i) <- lineStale.zipWithIndex) {
    e := MuxCase(
      e,
      Seq(
        io.flush            -> true.B,
        (lineAddr =/= i.U)  -> e,
        (y === S_MissReply) -> false.B
      )
    )
  }

  io.req.ready := y === S_Query

  io.resp.valid      := y.isOneOf(S_HitReply, S_MissReply)
  io.resp.bits.data  := line.data
  io.resp.bits.rResp := line.rResp

  io.memReq.valid     := y === S_MissReq
  io.memReq.bits.addr := io.req.bits.addr
  io.memReq.bits.size := io.req.bits.size

  io.memResp.ready := y === S_MissReply

  io.hit  := y === S_Compare & lineValid
  io.miss := y === S_Compare & ~lineValid
}
