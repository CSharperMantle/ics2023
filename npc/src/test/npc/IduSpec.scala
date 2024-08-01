package npc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class IduSpec extends AnyFlatSpec with ChiselScalatestTester {
  "Idu" should "decode combinationally" in {
    test(new Idu) { dut =>
      dut.reset.poke(true.B)
      step()
      dut.reset.poke(false.B)

      dut.io.msgIn.valid.poke(false)
      dut.io.msgOut.ready.poke(false)
      step()
      dut.io.msgOut.valid.expect(false)
      dut.io.msgIn.ready.expect(false)

      dut.io.msgIn.bits.instr.poke("b0000000_00001_00000_000_00000_11100_11".U) // ebreak
      dut.io.msgIn.valid.poke(true)
      step()
      dut.io.msgOut.valid.expect(true)
      dut.io.msgIn.ready.expect(false)

      dut.io.msgOut.ready.poke(true)
      step()
      dut.io.msgIn.ready.expect(true)

      dut.io.msgIn.valid.poke(false)
      step()
      dut.io.msgOut.valid.expect(false)

      dut.io.msgIn.valid.poke(true)
      dut.io.msgIn.bits.instr.poke("b0000000_00000_00000_000_00000_11100_11".U) // ecall
      dut.io.msgOut.ready.poke(true)
      step()
      dut.io.msgOut.valid.expect(true)
      dut.io.break.expect(false)
      dut.io.msgIn.ready.expect(true)
    }
  }
}
