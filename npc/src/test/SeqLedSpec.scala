package top

import chisel3._
import chiseltest._
import org.scalatest.matchers.must.Matchers
import org.scalatest.flatspec.AnyFlatSpec

class SeqLedSpec extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "rotate correctly" in {
    test(new SeqLed) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)

      for (i <- 0 until 16) {
        dut.io.leds.expect(1 << (i % 8))
        dut.clock.step()
      }
    }
  }
}
