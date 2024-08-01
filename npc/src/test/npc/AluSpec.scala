package npc

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.Random

import npc._

class AluSpec extends AnyFlatSpec {
  import AluCalcOp._
  import AluCalcDir._
  import AluBrCond._
  import MemWidth._

  val SEED    = 114514
  val N_CASES = 128

  behavior of "Alu"

  it should "calculate combinationally" in {
    simulate(new Alu) { dut =>
      val rand = new Random(SEED)
      dut.reset.poke(true)
      dut.clock.step()
      dut.reset.poke(false)

      dut.io.calcOp.poke(Add.U)
      dut.io.calcDir.poke(Pos.U)
      for (_ <- 0 until N_CASES) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        val y = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(y)
        dut.io.d.expect((x + y).ontoZmod2pow(XLen))
      }
      dut.io.calcDir.poke(Neg.U)
      for (_ <- 0 until N_CASES) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        val y = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(y)
        dut.io.d.expect((x - y).ontoZmod2pow(XLen))
      }

      dut.io.calcOp.poke(Sl.U)
      for (shamt <- 0 until XLen) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(shamt)
        dut.io.d.expect((x << shamt).ontoZmod2pow(XLen))
      }

      dut.io.calcOp.poke(Slt.U)
      for (_ <- 0 until N_CASES) {
        val x = if (XLen == 32) rand.nextInt() else rand.nextLong()
        val y = if (XLen == 32) rand.nextInt() else rand.nextLong()
        dut.io.s1.poke(BigInt(x).ontoZmod2pow(XLen))
        dut.io.s2.poke(BigInt(y).ontoZmod2pow(XLen))
        dut.io.d.expect(if (x < y) 1 else 0)
      }

      dut.io.calcOp.poke(Sltu.U)
      for (_ <- 0 until N_CASES) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        val y = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(y)
        dut.io.d.expect(if (x < y) 1 else 0)
      }

      dut.io.calcOp.poke(Xor.U)
      for (_ <- 0 until N_CASES) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        val y = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(y)
        dut.io.d.expect(x ^ y)
      }

      dut.io.calcOp.poke(Sr.U)
      dut.io.calcDir.poke(Pos.U)
      for (shamt <- 0 until XLen) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(shamt)
        dut.io.d.expect(x >> shamt)
      }
      dut.io.calcDir.poke(Neg.U)
      for (shamt <- 0 until XLen) {
        val x = if (XLen == 32) rand.nextInt() else rand.nextLong()
        dut.io.s1.poke(BigInt(x).ontoZmod2pow(XLen))
        dut.io.s2.poke(shamt)
        dut.io.d.expect(BigInt(x >> shamt).ontoZmod2pow(XLen))
      }

      dut.io.calcOp.poke(Or.U)
      for (_ <- 0 until N_CASES) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        val y = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(y)
        dut.io.d.expect(x | y)
      }

      dut.io.calcOp.poke(And.U)
      for (_ <- 0 until N_CASES) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        val y = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(y)
        dut.io.d.expect(x & y)
      }
    }
  }

  it should "produce branch conditions combinationally" in {
    simulate(new Alu) { dut =>
      val rand = new Random(SEED)
      dut.reset.poke(true)
      dut.clock.step()
      dut.reset.poke(false)

      val cases = Seq(
        (1, 1, Eq, true),
        (1, 1, Ne, false),
        (1, 1, Lt, false),
        (1, 1, Ge, true),
        (1, 1, Ltu, false),
        (1, 1, Geu, true),
        (-1, 1, Eq, false),
        (-1, 1, Ne, true),
        (-1, 1, Lt, true),
        (-1, 1, Ge, false),
        (-1, 1, Ltu, false),
        (-1, 1, Geu, true),
        (1, -1, Eq, false),
        (1, -1, Ne, true),
        (1, -1, Lt, false),
        (1, -1, Ge, true),
        (1, -1, Ltu, true),
        (1, -1, Geu, false),
        (0, 1, Eq, false),
        (0, 1, Ne, true),
        (0, 1, Lt, true),
        (0, 1, Ge, false),
        (0, 1, Ltu, true),
        (0, 1, Geu, false),
        (0, -1, Eq, false),
        (0, -1, Ne, true),
        (0, -1, Lt, false),
        (0, -1, Ge, true),
        (0, -1, Ltu, true),
        (0, -1, Geu, false),
        (1, 0, Eq, false),
        (1, 0, Ne, true),
        (1, 0, Lt, false),
        (1, 0, Ge, true),
        (1, 0, Ltu, false),
        (1, 0, Geu, true),
        (-1, 0, Eq, false),
        (-1, 0, Ne, true),
        (-1, 0, Lt, true),
        (-1, 0, Ge, false),
        (-1, 0, Ltu, false),
        (-1, 0, Geu, true)
      )

      for (c <- cases) {
        dut.io.s1.poke(BigInt(c._1).ontoZmod2pow(XLen))
        dut.io.s2.poke(BigInt(c._2).ontoZmod2pow(XLen))
        dut.io.brCond.poke(c._3.U)
        dut.io.brTaken.expect(c._4.B)
      }
    }
  }

  it should "assert brInvalid on invalid branch conditions" in {
    simulate(new Alu) { dut =>
      dut.reset.poke(true)
      dut.clock.step()
      dut.reset.poke(false)

      dut.io.brCond.poke(Eq.U)
      dut.io.brInvalid.expect(false.B)

      dut.io.brCond.poke(Unk.U)
      dut.io.brInvalid.expect(true.B)
    }
  }
}
