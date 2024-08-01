package npc

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class IduSpec extends AnyFlatSpec {
  behavior of "Idu"

  it should "decode combinationally" in {
    simulate(new Idu) { dut =>
      dut.reset.poke(true)
      dut.clock.step()
      dut.reset.poke(false)

      dut.io.msgIn.valid.poke(false)
      dut.io.msgOut.ready.poke(false)
      dut.clock.step()
      dut.io.msgOut.valid.expect(false.B)
      dut.io.msgIn.ready.expect(false.B)

      dut.io.msgIn.bits.instr.poke("b0000000_00001_00000_000_00000_11100_11".U) // ebreak
      dut.io.msgIn.valid.poke(true)
      dut.clock.step()
      dut.io.msgOut.valid.expect(true.B)
      dut.io.msgIn.ready.expect(false.B)

      dut.io.msgOut.ready.poke(true)
      dut.clock.step()
      dut.io.msgIn.ready.expect(true.B)

      dut.io.msgIn.valid.poke(false)
      dut.clock.step()
      dut.io.msgOut.valid.expect(false.B)

      dut.io.msgIn.valid.poke(true)
      dut.io.msgIn.bits.instr.poke("b0000000_00000_00000_000_00000_11100_11".U) // ecall
      dut.io.msgOut.ready.poke(true)
      dut.clock.step()
      dut.io.msgOut.valid.expect(true.B)
      dut.io.break.expect(false.B)
      dut.io.msgIn.ready.expect(true.B)
    }
  }
}
