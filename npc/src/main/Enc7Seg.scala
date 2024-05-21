package top

import chisel3._
import chisel3.util._

class Enc7Seg extends Module {
  val io = IO(new Bundle {
    val x  = Input(UInt(4.W))
    val en = Input(Bool())
    val y  = Output(UInt(8.W))
  })

  io.y := Mux(
    io.en,
    MuxCase(
      ~(0.U(8.W)),
      Seq(
        (io.x === 0x0.U) -> ~("b11111100".U(8.W)),
        (io.x === 0x1.U) -> ~("b01100000".U(8.W)),
        (io.x === 0x2.U) -> ~("b11011010".U(8.W)),
        (io.x === 0x3.U) -> ~("b11110010".U(8.W)),
        (io.x === 0x4.U) -> ~("b01100110".U(8.W)),
        (io.x === 0x5.U) -> ~("b10110110".U(8.W)),
        (io.x === 0x6.U) -> ~("b10111110".U(8.W)),
        (io.x === 0x7.U) -> ~("b11100000".U(8.W)),
        (io.x === 0x8.U) -> ~("b11111110".U(8.W)),
        (io.x === 0x9.U) -> ~("b11110110".U(8.W)),
        (io.x === 0xa.U) -> ~("b11101110".U(8.W)),
        (io.x === 0xb.U) -> ~("b00111110".U(8.W)),
        (io.x === 0xc.U) -> ~("b00011010".U(8.W)),
        (io.x === 0xd.U) -> ~("b01111010".U(8.W)),
        (io.x === 0xe.U) -> ~("b10011110".U(8.W)),
        (io.x === 0xf.U) -> ~("b10001110".U(8.W))
      )
    ),
    ~(0.U(8.W))
  )
}
