package npc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class GprFileSpec extends AnyFlatSpec with ChiselScalatestTester {
  "GprFile" should "write sequentially and read combinationally" in {
    test(new GprFile) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)

      for (_ <- 0 until 4) {
        dut.io.wEn.poke(true.B)
        for (i <- 0 until 32) {
          dut.io.rdIdx.poke(i)
          dut.io.rdData.poke(i + 1)
          dut.clock.step()
        }
        dut.io.wEn.poke(false.B)

        for (i <- 0 until 32) {
          val i_rev = 31 - i
          dut.io.rs1Idx.poke(i)
          dut.io.rs2Idx.poke(i_rev)
          dut.io.rs1.expect(
            if (i == 0) { 0 }
            else { i + 1 }
          )
          dut.io.rs2.expect(
            if (i_rev == 0) { 0 }
            else { i_rev + 1 }
          )
        }
      }
    }
  }
}
