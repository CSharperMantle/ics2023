package top

import chisel3._
import top.SeqLed

class Top extends Module {
  val io = IO(
    new Bundle {
      val leds = Output(UInt(8.W))
    }
  )

  val seq_led = Module(new SeqLed)

  io.leds := seq_led.io.leds
}
