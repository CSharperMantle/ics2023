package npc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import npc._

class NpcSpec extends AnyFlatSpec with ChiselScalatestTester {
  "Npc" should "assert break signal on EBREAK" in {
    test(new Npc) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)

      dut.io.break.expect(false)
      dut.io.instr.poke("b0000000_00001_00000_000_00000_11100_11".U) // ebreak
      dut.clock.step()
      dut.io.break.expect(true)
    }
  }

  it should "advance PC by 4 when running sequentially" in {
    test(new Npc) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)

      dut.io.pc.expect(InitPCVal)
      for (i <- 1 until 32) {
        dut.io.instr.poke("b0000000_00000_00000_000_00000_00100_11".U) // addi $0, $0, 0
        dut.clock.step()
        dut.io.pc.expect(InitPCVal + i * 4)
      }
    }
  }

  it should "perform additions by 1 on registers" in {
    test(new Npc) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)

      dut.io.instr.poke("b0000000_00001_00000_000_00001_00100_11".U) // addi ra, $0, 0x1
      dut.clock.step()
      for (i <- 0 until 32) {
        dut.io.instr.poke("b0000000_00001_00001_000_00001_00100_11".U) // addi ra, ra, 0x1
        dut.clock.step()
        dut.io.ra.expect(i + 2)
      }
    }
  }

  it should "request a word-sized read from memory" in {
    test(new Npc) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)

      dut.io.instr.poke("b0000000_00001_00000_010_00001_00000_11".U) // addi ra, ra, 0x1
      dut.io.memREn.expect(true.B)
      dut.io.memRAddr.expect(0x1)
      dut.io.memLen.expect(MemWidth.LenW.U)
      dut.io.memRData.poke(BigInt("deadbeef", 16))
      dut.clock.step()

      dut.io.instr.poke("b0000000_00000_00000_000_00000_00100_11".U) // addi $0, $0, 0
      dut.clock.step()

      dut.io.ra.expect(BigInt("deadbeef", 16))
    }
  }
}
