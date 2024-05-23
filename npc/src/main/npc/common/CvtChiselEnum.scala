package npc.common

import chisel3._
import chisel3.util._

abstract trait CvtChiselEnum extends ChiselEnum {
  implicit class CvtValueToType(v: Type) {
    def U: UInt = v.litValue.U(getWidth.W)

    def BP: BitPat = BitPat(v.U)
  }
}
