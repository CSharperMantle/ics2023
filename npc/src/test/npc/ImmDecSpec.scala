package npc

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

import npc._

class ImmDecSpec extends AnyFlatSpec {
  import ImmFmt._

  behavior of "ImmDec"

  it should "extract immediate combinationally" in {
    simulate(new ImmDec) { dut =>
      dut.reset.poke(true)
      dut.clock.step()
      dut.reset.poke(false)

      dut.io.instr.poke("b0000000_01110_11011_000_01110_01100_11".U) // add a4, s11, a4
      dut.io.immFmt.poke(ImmR.U)
      dut.io.imm.expect(BigInt("0", 16).ontoZmod2pow(XLen))

      dut.io.instr.poke("b1111111_10000_00010_000_00010_00100_11".U) // addi sp, sp, -0x10
      dut.io.immFmt.poke(ImmI.U)
      dut.io.imm.expect(BigInt("-10", 16).ontoZmod2pow(XLen))

      dut.io.instr.poke("b0000000_01000_00010_010_01000_01000_11".U) // sw s0, 8(sp)
      dut.io.immFmt.poke(ImmS.U)
      dut.io.imm.expect(BigInt("8", 16).ontoZmod2pow(XLen))

      dut.io.instr.poke("b0011010_10101_01010_000_10000_11000_11".U) // beq a0, s5, $+0x350
      dut.io.immFmt.poke(ImmB.U)
      dut.io.imm.expect(BigInt("350", 16).ontoZmod2pow(XLen))

      dut.io.instr.poke("b0000001_00110_11110_101_10100_00101_11".U) // auipc s4, 0x26f5000
      dut.io.immFmt.poke(ImmU.U)
      dut.io.imm.expect(BigInt("26f5000", 16).ontoZmod2pow(XLen))

      dut.io.instr.poke("b1111110_10100_11111_111_00001_11011_11".U) // jal ra, $-0x82c
      dut.io.immFmt.poke(ImmJ.U)
      dut.io.imm.expect(BigInt("-82c", 16).ontoZmod2pow(XLen))

      dut.io.instr.poke("b0000000_10110_01011_101_01111_00100_11".U) // srli a5, a1, 0x16
      dut.io.immFmt.poke(ImmIs.U)
      dut.io.imm.expect(BigInt("16", 16).ontoZmod2pow(XLen))

      dut.io.instr.poke("b0011010_00001_00111_001_00000_11100_11".U) // csrrw $0, mepc, t2
      dut.io.immFmt.poke(ImmIcsr.U)
      dut.io.imm.expect(BigInt("341", 16).ontoZmod2pow(XLen))
    }
  }
}
