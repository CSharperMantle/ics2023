package npc

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

import common._
import npc._

class SExtenderSpec extends AnyFlatSpec {
  import MemWidth._

  behavior of "SExtender"

  it should "sign-extend combinationally" in {
    simulate(new SExtender) { dut =>
      dut.reset.poke(true)
      dut.clock.step()
      dut.reset.poke(false)

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
