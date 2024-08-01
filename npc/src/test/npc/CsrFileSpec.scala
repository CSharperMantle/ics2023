package npc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import common._
import npc._

class CsrFileSpec extends AnyFlatSpec with ChiselScalatestTester {
  import CsrOp._

  "CsrFile" should "perform ops sequentially and return old value combinationally" in {
    test(new CsrFile) { dut =>
      dut.reset.poke(true.B)
      step()
      dut.reset.poke(false.B)

      dut.io.conn.csrIdx.poke("h340".U)
      dut.io.conn.s1.poke("hdeadbeef".U)
      dut.io.conn.csrOp.poke(Unk.U)
      dut.io.conn.csrVal.expect(0.U)
      step()

      dut.io.conn.csrOp.poke(Rs.U)
      step()
      dut.io.conn.csrVal.expect("hdeadbeef".U)

      dut.io.conn.csrOp.poke(Rc.U)
      dut.io.conn.s1.poke("hcafebabe".U)
      dut.io.conn.csrVal.expect("hdeadbeef".U)
      step()
      dut.io.conn.csrVal.expect("h14010441".U)

      dut.io.conn.csrOp.poke(Rw.U)
      dut.io.conn.s1.poke("h1234abcd".U)
      dut.io.conn.csrVal.expect("h14010441".U)
      step()
      dut.io.conn.csrVal.expect("h1234abcd".U)
    }
  }
}
