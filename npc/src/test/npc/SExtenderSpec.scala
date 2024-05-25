package npc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import common._
import npc._

class SExtenderSpec extends AnyFlatSpec with ChiselScalatestTester {
  import MemWidth._

  "SExtender" should "sign-extend combinationally" in {
    test(new SExtender) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)

      dut.io.sextW.poke(LenB.U)
      dut.io.sextData.poke(BigInt(-1).ontoZmod2pow(8))
      dut.io.sextRes.expect(BigInt(-1).ontoZmod2pow(XLen))
      dut.io.sextData.poke(BigInt(1).ontoZmod2pow(8))
      dut.io.sextRes.expect(BigInt(1).ontoZmod2pow(XLen))

      dut.io.sextW.poke(LenH.U)
      dut.io.sextData.poke(BigInt(-1).ontoZmod2pow(16))
      dut.io.sextRes.expect(BigInt(-1).ontoZmod2pow(XLen))
      dut.io.sextData.poke(BigInt(1).ontoZmod2pow(16))
      dut.io.sextRes.expect(BigInt(1).ontoZmod2pow(XLen))

      dut.io.sextW.poke(LenW.U)
      dut.io.sextData.poke(BigInt(-1).ontoZmod2pow(32))
      dut.io.sextRes.expect(BigInt(-1).ontoZmod2pow(XLen))
      dut.io.sextData.poke(BigInt(1).ontoZmod2pow(32))
      dut.io.sextRes.expect(BigInt(1).ontoZmod2pow(XLen))
    }
  }
}
