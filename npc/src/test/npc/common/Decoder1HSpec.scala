package npc.common

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import _root_.npc._
import npc._
import common.Decoder1H

class Decoder1HSpec extends AnyFlatSpec with ChiselScalatestTester {
  class Harness extends Module {
    val io = IO(new Bundle {
      val data = Input(UInt(4.W))
      val res  = Output(UInt(5.W))
    })

    private val dataDec = Decoder1H(
      Seq(
        "b1000".BP -> 0,
        "b1111".BP -> 1,
        "b101?".BP -> 2,
        "b0?0?".BP -> 3
      )
    )

    io.res := dataDec(io.data)
  }

  "Decoder1H" should "decode bit patterns into one-hot vector" in {
    test(new Harness) { dut =>
      dut.io.data.poke("b1000".U)
      dut.io.res.expect("b00001".U)

      dut.io.data.poke("b1111".U)
      dut.io.res.expect("b00010".U)

      dut.io.data.poke("b1010".U)
      dut.io.res.expect("b00100".U)

      dut.io.data.poke("b1011".U)
      dut.io.res.expect("b00100".U)

      dut.io.data.poke("b0000".U)
      dut.io.res.expect("b01000".U)

      dut.io.data.poke("b0100".U)
      dut.io.res.expect("b01000".U)

      dut.io.data.poke("b0010".U)
      dut.io.res.expect("b10000".U)

      dut.io.data.poke("b0111".U)
      dut.io.res.expect("b10000".U)
    }
  }
}

class MultiDecoder1HSpec extends AnyFlatSpec with ChiselScalatestTester {
  class Harness extends Module {
    val io = IO(new Bundle {
      val data = Input(UInt(4.W))
      val res  = Output(UInt(5.W))
    })

    private val dataDec = MultiDecoder1H(
      Seq(
        Seq("b1000".BP, "b011?".BP) -> 0,
        Seq("b1111".BP)             -> 1,
        Seq("b101?".BP)             -> 2,
        Seq("b0?0?".BP)             -> 3
      )
    )

    io.res := dataDec(io.data)
  }

  "MultiDecoder1H" should "decode bit patterns into one-hot vector" in {
    test(new Harness) { dut =>
      dut.io.data.poke("b1000".U)
      dut.io.res.expect("b00001".U)

      dut.io.data.poke("b0110".U)
      dut.io.res.expect("b00001".U)

      dut.io.data.poke("b0111".U)
      dut.io.res.expect("b00001".U)

      dut.io.data.poke("b1111".U)
      dut.io.res.expect("b00010".U)

      dut.io.data.poke("b1010".U)
      dut.io.res.expect("b00100".U)

      dut.io.data.poke("b1011".U)
      dut.io.res.expect("b00100".U)

      dut.io.data.poke("b0000".U)
      dut.io.res.expect("b01000".U)

      dut.io.data.poke("b0100".U)
      dut.io.res.expect("b01000".U)

      dut.io.data.poke("b0010".U)
      dut.io.res.expect("b10000".U)

      dut.io.data.poke("b0011".U)
      dut.io.res.expect("b10000".U)
    }
  }
}
