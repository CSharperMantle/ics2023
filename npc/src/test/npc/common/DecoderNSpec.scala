package npc.common

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class DecoderNSpec extends AnyFlatSpec with ChiselScalatestTester {
  "DecoderN" should "decode 5-bits input combinationally" in {
    test(new DecoderN(5.W)) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)

      for (i <- 0 until (1 << 5)) {
        dut.io.i_x.poke(i)
        dut.io.o_y.expect(BigInt(1) << i)
      }
    }
  }
}
