package npc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class GPRFileSpec extends AnyFlatSpec with ChiselScalatestTester {
  "GPRFile" should "write sequentially and read combinationally" in {
    test(new GPRFile) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)

      for (_ <- 0 until 4) {
        dut.io.wen.poke(true.B)
        for (i <- 0 until 32) {
          dut.io.rd_addr.poke(i)
          dut.io.rd_data.poke(i + 1)
          dut.clock.step()
        }
        dut.io.wen.poke(false.B)

        for (i <- 0 until 32) {
          val i_rev = 31 - i
          dut.io.rs1_addr.poke(i)
          dut.io.rs2_addr.poke(i_rev)
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
