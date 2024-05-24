package npc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import scala.util.Random

import npc._

class AluSpec extends AnyFlatSpec with ChiselScalatestTester {
  implicit class RandomExtension(rand: Random) {
    def nextBigIntW(width: Int): BigInt =
      BigInt((for (_ <- 0 until width) yield if (rand.nextBoolean()) "1" else "0").mkString)
  }

  implicit class BigIntExtension(x: BigInt) {
    def ontoZmod2pow(width: Int): BigInt = x & BigInt("1" * width, 2)
  }

  val SEED    = 114514
  val N_CASES = 128

  "Alu" should "perform calculations combinationally" in {
    test(new Alu) { dut =>
      val rand = new Random(SEED)
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)

      dut.io.calcOp.poke(AluCalcOp.Add.U)
      dut.io.calcDir.poke(AluCalcDir.Pos.U)
      for (_ <- 0 until N_CASES) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        val y = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(y)
        dut.io.d.expect((x + y).ontoZmod2pow(XLen))
      }
      dut.io.calcDir.poke(AluCalcDir.Neg.U)
      for (_ <- 0 until N_CASES) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        val y = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(y)
        dut.io.d.expect((x - y).ontoZmod2pow(XLen))
      }

      dut.io.calcOp.poke(AluCalcOp.Sl.U)
      for (shamt <- 0 until XLen) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(shamt)
        dut.io.d.expect((x << shamt).ontoZmod2pow(XLen))
      }

      dut.io.calcOp.poke(AluCalcOp.Slt.U)
      for (_ <- 0 until N_CASES) {
        val x = if (XLen == 32) rand.nextInt() else rand.nextLong()
        val y = if (XLen == 32) rand.nextInt() else rand.nextLong()
        dut.io.s1.poke(BigInt(x).ontoZmod2pow(XLen))
        dut.io.s2.poke(BigInt(y).ontoZmod2pow(XLen))
        dut.io.d.expect(if (x < y) 1 else 0)
      }

      dut.io.calcOp.poke(AluCalcOp.Sltu.U)
      for (_ <- 0 until N_CASES) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        val y = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(y)
        dut.io.d.expect(if (x < y) 1 else 0)
      }

      dut.io.calcOp.poke(AluCalcOp.Xor.U)
      for (_ <- 0 until N_CASES) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        val y = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(y)
        dut.io.d.expect(x ^ y)
      }

      dut.io.calcOp.poke(AluCalcOp.Sr.U)
      dut.io.calcDir.poke(AluCalcDir.Pos.U)
      for (shamt <- 0 until XLen) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(shamt)
        dut.io.d.expect(x >> shamt)
      }
      dut.io.calcDir.poke(AluCalcDir.Neg.U)
      for (shamt <- 0 until XLen) {
        val x = if (XLen == 32) rand.nextInt() else rand.nextLong()
        dut.io.s1.poke(BigInt(x).ontoZmod2pow(XLen))
        dut.io.s2.poke(shamt)
        dut.io.d.expect(BigInt(x >> shamt).ontoZmod2pow(XLen))
      }

      dut.io.calcOp.poke(AluCalcOp.Or.U)
      for (_ <- 0 until N_CASES) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        val y = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(y)
        dut.io.d.expect(x | y)
      }

      dut.io.calcOp.poke(AluCalcOp.And.U)
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
    test(new Alu) { dut =>
      val rand = new Random(SEED)
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)

      val cases = Seq(
        (1, 1, AluBrCond.Eq, true),
        (1, 1, AluBrCond.Ne, false),
        (1, 1, AluBrCond.Lt, false),
        (1, 1, AluBrCond.Ge, true),
        (1, 1, AluBrCond.Ltu, false),
        (1, 1, AluBrCond.Geu, true),
        (-1, 1, AluBrCond.Eq, false),
        (-1, 1, AluBrCond.Ne, true),
        (-1, 1, AluBrCond.Lt, true),
        (-1, 1, AluBrCond.Ge, false),
        (-1, 1, AluBrCond.Ltu, false),
        (-1, 1, AluBrCond.Geu, true),
        (1, -1, AluBrCond.Eq, false),
        (1, -1, AluBrCond.Ne, true),
        (1, -1, AluBrCond.Lt, false),
        (1, -1, AluBrCond.Ge, true),
        (1, -1, AluBrCond.Ltu, true),
        (1, -1, AluBrCond.Geu, false),
        (0, 1, AluBrCond.Eq, false),
        (0, 1, AluBrCond.Ne, true),
        (0, 1, AluBrCond.Lt, true),
        (0, 1, AluBrCond.Ge, false),
        (0, 1, AluBrCond.Ltu, true),
        (0, 1, AluBrCond.Geu, false),
        (0, -1, AluBrCond.Eq, false),
        (0, -1, AluBrCond.Ne, true),
        (0, -1, AluBrCond.Lt, false),
        (0, -1, AluBrCond.Ge, true),
        (0, -1, AluBrCond.Ltu, true),
        (0, -1, AluBrCond.Geu, false),
        (1, 0, AluBrCond.Eq, false),
        (1, 0, AluBrCond.Ne, true),
        (1, 0, AluBrCond.Lt, false),
        (1, 0, AluBrCond.Ge, true),
        (1, 0, AluBrCond.Ltu, false),
        (1, 0, AluBrCond.Geu, true),
        (-1, 0, AluBrCond.Eq, false),
        (-1, 0, AluBrCond.Ne, true),
        (-1, 0, AluBrCond.Lt, true),
        (-1, 0, AluBrCond.Ge, false),
        (-1, 0, AluBrCond.Ltu, false),
        (-1, 0, AluBrCond.Geu, true)
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
    test(new Alu) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)

      dut.io.brCond.poke(AluBrCond.Eq.U)
      dut.io.brInvalid.expect(false.B)

      dut.io.brCond.poke(AluBrCond.Unk.U)
      dut.io.brInvalid.expect(true.B)
    }
  }
}
