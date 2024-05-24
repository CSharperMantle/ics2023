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

      dut.io.op.poke(AluOp.Add.U)
      dut.io.dir.poke(AluDir.Pos.U)
      for (_ <- 0 until N_CASES) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        val y = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(y)
        dut.io.d.expect((x + y).ontoZmod2pow(XLen))
      }
      dut.io.dir.poke(AluDir.Neg.U)
      for (_ <- 0 until N_CASES) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        val y = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(y)
        dut.io.d.expect((x - y).ontoZmod2pow(XLen))
      }

      dut.io.op.poke(AluOp.Sl.U)
      for (shamt <- 0 until XLen) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(shamt)
        dut.io.d.expect((x << shamt).ontoZmod2pow(XLen))
      }

      dut.io.op.poke(AluOp.Slt.U)
      for (_ <- 0 until N_CASES) {
        val x = if (XLen == 32) rand.nextInt() else rand.nextLong()
        val y = if (XLen == 32) rand.nextInt() else rand.nextLong()
        dut.io.s1.poke(BigInt(x).ontoZmod2pow(XLen))
        dut.io.s2.poke(BigInt(y).ontoZmod2pow(XLen))
        dut.io.d.expect(if (x < y) 1 else 0)
      }

      dut.io.op.poke(AluOp.Sltu.U)
      for (_ <- 0 until N_CASES) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        val y = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(y)
        dut.io.d.expect(if (x < y) 1 else 0)
      }

      dut.io.op.poke(AluOp.Xor.U)
      for (_ <- 0 until N_CASES) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        val y = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(y)
        dut.io.d.expect(x ^ y)
      }

      dut.io.op.poke(AluOp.Sr.U)
      dut.io.dir.poke(AluDir.Pos.U)
      for (shamt <- 0 until XLen) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(shamt)
        dut.io.d.expect(x >> shamt)
      }
      dut.io.dir.poke(AluDir.Neg.U)
      for (shamt <- 0 until XLen) {
        val x = if (XLen == 32) rand.nextInt() else rand.nextLong()
        dut.io.s1.poke(BigInt(x).ontoZmod2pow(XLen))
        dut.io.s2.poke(shamt)
        dut.io.d.expect(BigInt(x >> shamt).ontoZmod2pow(XLen))
      }

      dut.io.op.poke(AluOp.Or.U)
      for (_ <- 0 until N_CASES) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        val y = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(y)
        dut.io.d.expect(x | y)
      }

      dut.io.op.poke(AluOp.And.U)
      for (_ <- 0 until N_CASES) {
        val x = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        val y = rand.nextBigIntW(XLen).ontoZmod2pow(XLen)
        dut.io.s1.poke(x)
        dut.io.s2.poke(y)
        dut.io.d.expect(x & y)
      }
    }
  }
}
