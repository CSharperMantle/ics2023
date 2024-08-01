package npc

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

import common._
import npc._

class CsrFileSpec extends AnyFlatSpec {
  import CsrOp._

  behavior of "CsrFile"

  it should "perform ops sequentially and return old value combinationally" in {
    simulate(new CsrFile) { dut =>
      dut.reset.poke(true)
      dut.clock.step()
      dut.reset.poke(false)

      dut.io.conn.csrIdx.poke("h340".U)
      dut.io.conn.s1.poke("hdeadbeef".U)
      dut.io.conn.csrOp.poke(Unk.U)
      dut.io.conn.csrVal.expect(0.U)
      dut.clock.step()

      dut.io.conn.csrOp.poke(Rs.U)
      dut.clock.step()
      dut.io.conn.csrVal.expect("hdeadbeef".U)

      dut.io.conn.csrOp.poke(Rc.U)
      dut.io.conn.s1.poke("hcafebabe".U)
      dut.io.conn.csrVal.expect("hdeadbeef".U)
      dut.clock.step()
      dut.io.conn.csrVal.expect("h14010441".U)

      dut.io.conn.csrOp.poke(Rw.U)
      dut.io.conn.s1.poke("h1234abcd".U)
      dut.io.conn.csrVal.expect("h14010441".U)
      dut.clock.step()
      dut.io.conn.csrVal.expect("h1234abcd".U)
    }
  }
}
