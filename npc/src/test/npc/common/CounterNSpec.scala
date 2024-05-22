package npc.common

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class CounterNSpec extends AnyFlatSpec with ChiselScalatestTester {
  "CounterN" should "count and reset in 4-bits correctly" in {
    test(new CounterN(5.W)) { dut =>
      for (_ <- 0 until 4) {
        dut.io.i_en.poke(false.B)
        dut.reset.poke(true.B)
        dut.clock.step()
        dut.reset.poke(false.B)

        var cnt = 0
        for (_ <- 0 until 16) {
          dut.io.i_en.poke(true.B)
          dut.clock.step()
          cnt = cnt + 1
          dut.io.o_q.expect(cnt)
          dut.io.i_en.poke(false.B)
          dut.clock.step()
          dut.io.o_q.expect(cnt)
        }
      }
    }
  }
}
