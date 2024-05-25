package npc.common

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import npc.npc._

class Decoder1H(val cases: Seq[(BitPat, Int)]) {
  private var _table: TruthTable = {
    TruthTable(
      cases.map {
        case (pat, idx) => {
          val pat1HSuffixed = if (idx > 0) (1.Y ## idx.N) else 1.Y
          pat -> (if (cases.length - idx > 0) ((cases.length - idx).N ## pat1HSuffixed) else pat1HSuffixed)
        }
      },
      1.Y ## cases.length.N
    )
  }

  def apply(x: UInt): UInt = {
    decoder(x, _table)
  }

  def bitBad: Int = cases.length
}

object Decoder1H {
  def apply(cases: Seq[(BitPat, Int)]): Decoder1H = new Decoder1H(cases)
}
