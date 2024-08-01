package npc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import scala.util.Random

import common._
import npc._

class XbarSpec extends AnyFlatSpec with ChiselScalatestTester {
  val SEED    = 114514
  val N_CASES = 128

  "Xbar" should "decode transaction address correctly" in {
    test(
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
      dut.reset.poke(true.B)
      step()
      dut.reset.poke(false.B)

      dut.io.masterReq.bits.poke("b0000".U)
      dut.io.masterReq.valid.poke(true)
      step()
      dut.io.slaveReq(0).valid.expect(true)
      dut.io.slaveReq(0).bits.expect("b0000".U)
      dut.io.slaveReq(1).valid.expect(false)

      dut.io.slaveReq(1).ready.poke(true)
      step()
      dut.io.masterReq.ready.expect(false)
      dut.io.slaveReq(0).ready.poke(true)
      dut.io.slaveReq(1).ready.poke(false)
      step()
      dut.io.masterReq.ready.expect(true)
      step()
      dut.io.slaveResp(0).valid.poke(true)
      dut.io.slaveResp(0).bits.poke(RResp.Okay.U)
      step()
      dut.io.masterResp.valid.expect(true)
      dut.io.masterResp.bits.expect(RResp.Okay.U)

      dut.io.masterResp.ready.poke(true)
      step()
      dut.io.slaveReq(0).ready.expect(true)

      dut.io.masterReq.valid.poke(false)
      dut.io.masterResp.ready.poke(false)
      for (i <- (0 until dut.n)) {
        dut.io.slaveReq(i).ready.poke(false)
        dut.io.slaveResp(i).valid.poke(false)
      }
      step()

      dut.io.masterReq.bits.poke("b0101".U)
      dut.io.masterReq.valid.poke(true)
      step()
      dut.io.slaveReq(0).valid.expect(true)
      dut.io.slaveReq(0).bits.expect("b0101".U)
      dut.io.slaveReq(1).valid.expect(false)

      dut.io.slaveReq(1).ready.poke(true)
      step()
      dut.io.masterReq.ready.expect(false)
      dut.io.slaveReq(0).ready.poke(true)
      dut.io.slaveReq(1).ready.poke(false)
      step()
      dut.io.masterReq.ready.expect(true)
      step()
      dut.io.slaveResp(0).valid.poke(true)
      dut.io.slaveResp(0).bits.poke(RResp.Okay.U)
      step()
      dut.io.masterResp.valid.expect(true)
      dut.io.masterResp.bits.expect(RResp.Okay.U)

      dut.io.masterResp.ready.poke(true)
      step()
      dut.io.slaveReq(0).ready.expect(true)
    }
  }

  it should "report invalid address quickly" in {
    test(
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
      dut.reset.poke(true.B)
      step()
      dut.reset.poke(false.B)

      dut.io.masterReq.bits.poke("b0110".U)
      dut.io.masterReq.valid.poke(true)
      step()
      dut.io.slaveReq(0).valid.expect(false)
      dut.io.slaveReq(1).valid.expect(false)
      dut.io.masterReq.ready.expect(true)

      step()
      dut.io.masterResp.valid.expect(true)
      dut.io.masterResp.bits.expect(RResp.DecErr.U)
      step()
      dut.io.masterResp.ready.poke(true)
      step()
      dut.io.slaveResp(0).ready.expect(false)
      dut.io.slaveResp(1).ready.expect(false)
    }
  }
}
