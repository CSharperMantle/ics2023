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
      dut.clock.step()
      dut.reset.poke(false.B)

      dut.io.csrIdx.poke("h340".U)
      dut.io.s1.poke("hdeadbeef".U)
      dut.io.csrOp.poke(Unk.U)
      dut.io.csrVal.expect(0.U)
      dut.clock.step()

      dut.io.csrOp.poke(Rs.U)
      dut.clock.step()
      dut.io.csrVal.expect("hdeadbeef".U)

      dut.io.csrOp.poke(Rc.U)
      dut.io.s1.poke("hcafebabe".U)
      dut.io.csrVal.expect("hdeadbeef".U)
      dut.clock.step()
      dut.io.csrVal.expect("h14010441".U)

      dut.io.csrOp.poke(Rw.U)
      dut.io.s1.poke("h1234abcd".U)
      dut.io.csrVal.expect("h14010441".U)
      dut.clock.step()
      dut.io.csrVal.expect("h1234abcd".U)
    }
  }
}
