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
          val idx1HSuf = if (idx > 0) (1.W.Y ## idx.W.N) else 1.W.Y
          pat -> (if (cases.length - idx > 0) ((cases.length - idx).W.N ## idx1HSuf) else idx1HSuf)
        }
      },
      1.W.Y ## cases.length.W.N
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
          val idx1HSuf = if (idx > 0) (1.W.Y ## idx.W.N) else 1.W.Y
          patIter.map((pat) =>
            pat -> (if (cases.length - idx > 0) ((cases.length - idx).W.N ## idx1HSuf)
                    else idx1HSuf)
          )
        }
      },
      1.W.Y ## cases.length.W.N
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
