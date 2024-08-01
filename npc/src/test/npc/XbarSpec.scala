package npc

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.Random

import common._
import npc._

class XbarSpec extends AnyFlatSpec {
  val SEED    = 114514
  val N_CASES = 128

  behavior of "Xbar"

  it should "decode transaction address correctly" in {
    simulate(
      new Xbar(
        UInt(4.W),
        UInt(2.W),
        Seq(
          Seq("b000?".BP, "b010?".BP),
          Seq("b100?".BP, "b1111".BP)
        ),
        (req: UInt) => req,
        (resp: UInt) => resp
      )
    ) { dut =>
      dut.reset.poke(true)
      dut.clock.step()
      dut.reset.poke(false)

      dut.io.masterReq.bits.poke("b0000".U)
      dut.io.masterReq.valid.poke(true)
      dut.clock.step()
      dut.io.slaveReq(0).valid.expect(true.B)
      dut.io.slaveReq(0).bits.expect("b0000".U)
      dut.io.slaveReq(1).valid.expect(false.B)

      dut.io.slaveReq(1).ready.poke(true)
      dut.clock.step()
      dut.io.masterReq.ready.expect(false.B)
      dut.io.slaveReq(0).ready.poke(true)
      dut.io.slaveReq(1).ready.poke(false)
      dut.clock.step()
      dut.io.masterReq.ready.expect(true.B)
      dut.clock.step()
      dut.io.slaveResp(0).valid.poke(true)
      dut.io.slaveResp(0).bits.poke(RResp.Okay.U)
      dut.clock.step()
      dut.io.masterResp.valid.expect(true.B)
      dut.io.masterResp.bits.expect(RResp.Okay.U)

      dut.io.masterResp.ready.poke(true)
      dut.clock.step()
      dut.io.slaveReq(0).ready.expect(true.B)

      dut.io.masterReq.valid.poke(false)
      dut.io.masterResp.ready.poke(false)
      for (i <- (0 until dut.n)) {
        dut.io.slaveReq(i).ready.poke(false)
        dut.io.slaveResp(i).valid.poke(false)
      }
      dut.clock.step()

      dut.io.masterReq.bits.poke("b0101".U)
      dut.io.masterReq.valid.poke(true)
      dut.clock.step()
      dut.io.slaveReq(0).valid.expect(true.B)
      dut.io.slaveReq(0).bits.expect("b0101".U)
      dut.io.slaveReq(1).valid.expect(false.B)

      dut.io.slaveReq(1).ready.poke(true)
      dut.clock.step()
      dut.io.masterReq.ready.expect(false.B)
      dut.io.slaveReq(0).ready.poke(true)
      dut.io.slaveReq(1).ready.poke(false)
      dut.clock.step()
      dut.io.masterReq.ready.expect(true.B)
      dut.clock.step()
      dut.io.slaveResp(0).valid.poke(true)
      dut.io.slaveResp(0).bits.poke(RResp.Okay.U)
      dut.clock.step()
      dut.io.masterResp.valid.expect(true.B)
      dut.io.masterResp.bits.expect(RResp.Okay.U)

      dut.io.masterResp.ready.poke(true)
      dut.clock.step()
      dut.io.slaveReq(0).ready.expect(true.B)
    }
  }

  it should "report invalid address quickly" in {
    simulate(
      new Xbar(
        UInt(4.W),
        UInt(2.W),
        Seq(
          Seq("b000?".BP, "b010?".BP),
          Seq("b100?".BP, "b1111".BP)
        ),
        (req: UInt) => req,
        (resp: UInt) => resp
      )
    ) { dut =>
      dut.reset.poke(true)
      dut.clock.step()
      dut.reset.poke(false)

      dut.io.masterReq.bits.poke("b0110".U)
      dut.io.masterReq.valid.poke(true)
      dut.clock.step()
      dut.io.slaveReq(0).valid.expect(false.B)
      dut.io.slaveReq(1).valid.expect(false.B)
      dut.io.masterReq.ready.expect(true.B)

      dut.clock.step()
      dut.io.masterResp.valid.expect(true.B)
      dut.io.masterResp.bits.expect(RResp.DecErr.U)
      dut.clock.step()
      dut.io.masterResp.ready.poke(true)
      dut.clock.step()
      dut.io.slaveResp(0).ready.expect(false.B)
      dut.io.slaveResp(1).ready.expect(false.B)
    }
  }
}
