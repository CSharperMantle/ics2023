package npc.common

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

import npc.npc._

class Decoder1H(val cases: Seq[(BitPat, Int)]) {
  private val _table: TruthTable = {
    TruthTable(
      cases.map {
        case (pat, idx) => {
          val idx1HSuf = if (idx > 0) (1.Y ## idx.N) else 1.Y
          pat -> (if (cases.length - idx > 0) ((cases.length - idx).N ## idx1HSuf)
                  else idx1HSuf)
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

class MultiDecoder1H(val cases: Seq[(Iterable[BitPat], Int)]) {
  private val _table: TruthTable = {
    TruthTable(
      cases.flatMap {
        case (patIter, idx) => {
          val idx1HSuf = if (idx > 0) (1.Y ## idx.N) else 1.Y
          val idx1H =
            (if (cases.length - idx > 0) ((cases.length - idx).N ## idx1HSuf)
             else idx1HSuf)
          patIter.map((pat) => pat -> idx1H)
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

object MultiDecoder1H {
  def apply(cases: Seq[(Iterable[BitPat], Int)]): MultiDecoder1H = new MultiDecoder1H(cases)
}
