package top

import chisel3._

class SeqLed extends Module {
  val io = IO(
    new Bundle {
      val leds = Output(UInt(8.W))
    }
  )

  val led_state = RegInit(1.U(8.W))
  led_state := (led_state << 1) | (led_state >> 7)

  io.leds := led_state
}
