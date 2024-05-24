package npc

import chisel3._
import chisel3.util._

object npc {
  implicit class CvtIntToType(w: Int) {
    def Y: BitPat = BitPat.Y(w)
    def N: BitPat = BitPat.N(w)
    def X: BitPat = BitPat.dontCare(w)
  }

  implicit class CvtStringToType(s: String) {
    def BP: BitPat = BitPat(s)
  }

  val XLen = 32
  require(XLen == 32 || XLen == 64)

  val InitPCVal = BigInt("80000000", 16)
}
