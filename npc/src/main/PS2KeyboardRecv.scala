package top

import chisel3._

class PS2KeyboardRecv extends Module {
  val io = IO(
    new Bundle {
      val ps2_clk  = Input(Bool())
      val ps2_data = Input(Bool())
      val nxt_n    = Input(Bool())
      val data     = Output(UInt(8.W))
      val ready    = Output(Bool())
      val overflow = Output(Bool())
    }
  )

  val reg_buffer   = RegInit(VecInit(Seq.fill(10)(false.B)))
  val reg_fifo     = RegInit(VecInit(Seq.fill(8)(0.U(8.W))))
  val reg_w_ptr    = RegInit(0.U(3.W))
  val reg_r_ptr    = RegInit(0.U(3.W))
  val reg_count    = RegInit(0.U(4.W))
  val reg_ps2_sync = RegInit(0.U(3.W))
  val reg_ready    = RegInit(false.B)
  val reg_overflow = RegInit(false.B)

  val sampling = reg_ps2_sync(2) & ~reg_ps2_sync(1)

  reg_ps2_sync := reg_ps2_sync(1, 0) ## io.ps2_clk

  when(reg_ready) {
    when(io.nxt_n === false.B) {
      reg_r_ptr := reg_r_ptr + 1.U
      when(reg_w_ptr === (reg_r_ptr + 1.U)) {
        reg_ready := false.B
      }.otherwise {}
    }.otherwise {}
  }.otherwise {}
  when(sampling) {
    when(reg_count === 10.U(4.W)) {
      when(reg_buffer(0) === false.B && io.ps2_data && reg_buffer.asUInt(9, 1).xorR) {
        reg_fifo(reg_w_ptr) := reg_buffer.asUInt(8, 1)
        reg_w_ptr           := reg_w_ptr + 1.U
        reg_ready            := true.B
        reg_overflow         := reg_overflow | (reg_r_ptr === (reg_w_ptr + 1.U))
      }.otherwise {}
      reg_count := 0.U
    }.otherwise {
      reg_buffer(reg_count) := io.ps2_data
      reg_count             := reg_count + 1.U
    }
  }.otherwise {}

  io.data     := reg_fifo(reg_r_ptr)
  io.ready    := reg_ready
  io.overflow := reg_overflow
}
