package npc

import chisel3._
import chisel3.util._

import scala.util.Random

object npc {
  implicit class CvtIntToType(w: Int) {
    def Y: BitPat = BitPat.Y(w)
    def N: BitPat = BitPat.N(w)
    def X: BitPat = BitPat.dontCare(w)
  }

  implicit class CvtStringToType(s: String) {
    def BP: BitPat = BitPat(s)
  }

  implicit class RandomExtension(rand: Random) {
    def nextBigIntW(width: Int): BigInt =
      BigInt((for (_ <- 0 until width) yield if (rand.nextBoolean()) "1" else "0").mkString)
  }

  implicit class BigIntExtension(x: BigInt) {
    def ontoZmod2pow(width: Int): BigInt = x & BigInt("1" * width, 2)
  }

  val XLen = 32
  require(XLen == 32 || XLen == 64)

  val InitPCVal = BigInt("80000000", 16)
}
