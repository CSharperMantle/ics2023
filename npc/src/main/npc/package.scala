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

  object ArchType extends Enumeration {
    type ArchType = Value

    val SingleCycle = Value
    val MultiCycle  = Value
    val Pipelined   = Value
    val OutOfOrder  = Value
  }

  val Arch = ArchType.MultiCycle

  val XLen = 32
  require(XLen == 32 || XLen == 64)
  require(XLen == 32, "RV64 not implemented")

  val InitPCVal = BigInt("80000000", 16)

  object PrivMode {
    val M = 3
    val S = 1
    val U = 0
  }

  object IntrCode {
    val SSoft     = 1
    val MSoft     = 3
    val STimer    = 5
    val MTimer    = 7
    val SExternal = 9
    val MExternal = 11
  }

  object ExcpCode {
    val InstUnaligned  = 0
    val InstAccess     = 1
    val Inst           = 2
    val Break          = 3
    val ReadUnaligned  = 4
    val ReadAccess     = 5
    val StoreUnaligned = 6
    val StoreAccess    = 7
    val UEnvCall       = 8
    val SEnvCall       = 9
    val MEnvCall       = 11
    val InstPage       = 12
    val ReadPage       = 13
    val StorePage      = 14
  }

  def getDpiType(width: Width): String = {
    return width match {
      case KnownWidth(value) =>
        value match {
          case 1  => "bool"
          case 8  => "byte"
          case 16 => "shortint"
          case 32 => "int"
          case 64 => "longint"
          case _  => throw new IllegalArgumentException
        }
      case UnknownWidth() => throw new IllegalArgumentException
    }
  }

  object BResp {
    val Okay   = "b00".U(2.W)
    val ExOkay = "b01".U(2.W)
    val SlvErr = "b10".U(2.W)
    val DecErr = "b11".U(2.W)
  }

  object RResp {
    val Okay   = "b00".U(2.W)
    val ExOkay = "b01".U(2.W)
    val SlvErr = "b10".U(2.W)
    val DecErr = "b11".U(2.W)
  }
}
