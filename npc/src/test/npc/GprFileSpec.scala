package npc

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class GprFileSpec extends AnyFlatSpec {
  behavior of "GprFile"

  it should "write sequentially and read combinationally" in {
    simulate(new GprFile) { dut =>
      dut.reset.poke(true)
      dut.clock.step()
      dut.reset.poke(false)

      for (_ <- 0 until 4) {
        dut.io.write.wEn.poke(true.B)
        for (i <- 0 until 32) {
          dut.io.write.rdIdx.poke(i)
          dut.io.write.rdData.poke(i + 1)
          dut.clock.step()
        }
        dut.io.write.wEn.poke(false.B)

        for (i <- 0 until 32) {
          val i_rev = 31 - i
          dut.io.read.rs1Idx.poke(i)
          dut.io.read.rs2Idx.poke(i_rev)
          dut.io.read.rs1.expect(
            if (i == 0) { 0 }
            else { i + 1 }
          )
          dut.io.read.rs2.expect(
            if (i_rev == 0) { 0 }
            else { i_rev + 1 }
          )
        }
      }
    }
  }
}
