package top

import chisel3._
import chisel3.util._

class Enc7Seg extends Module {
  val io = IO(new Bundle {
    val x  = Input(UInt(4.W))
    val en = Input(Bool())
    val y  = Output(UInt(8.W))
  })

  io.y := ~(0.U(8.W))
  when(io.en) {
    switch(io.x) {
      is(0x0.U) { io.y := ~("b11111100".U(8.W)) }
      is(0x1.U) { io.y := ~("b01100000".U(8.W)) }
      is(0x2.U) { io.y := ~("b11011010".U(8.W)) }
      is(0x3.U) { io.y := ~("b11110010".U(8.W)) }
      is(0x4.U) { io.y := ~("b01100110".U(8.W)) }
      is(0x5.U) { io.y := ~("b10110110".U(8.W)) }
      is(0x6.U) { io.y := ~("b10111110".U(8.W)) }
      is(0x7.U) { io.y := ~("b11100000".U(8.W)) }
      is(0x8.U) { io.y := ~("b11111110".U(8.W)) }
      is(0x9.U) { io.y := ~("b11110110".U(8.W)) }
      is(0xa.U) { io.y := ~("b11101110".U(8.W)) }
      is(0xb.U) { io.y := ~("b00111110".U(8.W)) }
      is(0xc.U) { io.y := ~("b00011010".U(8.W)) }
      is(0xd.U) { io.y := ~("b01111010".U(8.W)) }
      is(0xe.U) { io.y := ~("b10011110".U(8.W)) }
      is(0xf.U) { io.y := ~("b10001110".U(8.W)) }
    }
  }.otherwise {}
}
