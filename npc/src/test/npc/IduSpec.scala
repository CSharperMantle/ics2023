package npc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class IduSpec extends AnyFlatSpec with ChiselScalatestTester {
  "Idu" should "assert break signal on EBREAK" in {
    test(new Idu) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)

      dut.io.instr.poke("b0000000_00001_00000_000_00000_11100_11".U) // ebreak
      dut.io.break.expect(true)
      dut.io.instr.poke("b0000000_00000_00000_000_00000_11100_11".U) // ecall
      dut.io.break.expect(false)
    }
  }
}
