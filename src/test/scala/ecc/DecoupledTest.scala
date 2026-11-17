package ecc

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/** Basic checks of the ready/valid wrappers. Random stalls are tested separately. */
/** Encoder connected to decoder with `<>`, as shown in the README */
class DecoupledChain(code: HammingCode) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(UInt(code.dataWidth.W)))
    val out = Decoupled(new DecodedWord(code))
  })
  val enc = Module(new HammingEncoderDecoupled(code))
  val dec = Module(new HammingDecoderDecoupled(code))
  enc.io.in <> io.in
  enc.io.out <> dec.io.in
  io.out <> dec.io.out
}

class DecoupledTest extends AnyFlatSpec with ChiselScalatestTester {

  "Chained encoder and decoder" should "return the data and connect the handshake" in {
    test(new DecoupledChain(HammingCode(8, secded = true))) { dut =>
      for (data <- Seq(0x00, 0x69, 0xff)) {
        dut.io.in.bits.poke(data.U)
        dut.io.in.valid.poke(true.B)
        dut.io.out.ready.poke(true.B)
        dut.io.out.valid.expect(true.B)
        dut.io.out.bits.data.expect(data.U)
        dut.io.out.bits.corrected.expect(false.B)
        dut.io.out.bits.uncorrectable.expect(false.B)

        dut.io.out.ready.poke(false.B)
        dut.io.in.ready.expect(false.B)
      }
    }
  }

  for (secded <- Seq(false, true)) {
    val code = HammingCode(8, secded)

    s"HammingEncoderDecoupled ($code)" should "pass valid and ready through and encode like the model" in {
      test(new HammingEncoderDecoupled(code)) { dut =>
        for (data <- Seq(0x00, 0x01, 0x69, 0xa5, 0xff)) {
          dut.io.in.bits.poke(data.U)

          // no data offered: nothing valid on the output
          dut.io.in.valid.poke(false.B)
          dut.io.out.ready.poke(true.B)
          dut.io.out.valid.expect(false.B)

          // data offered and receiver ready: the word moves
          dut.io.in.valid.poke(true.B)
          dut.io.out.valid.expect(true.B)
          dut.io.in.ready.expect(true.B)
          dut.io.out.bits.expect(HammingModel.encode(code, data).U)

          // receiver not ready: the sender is told to wait
          dut.io.out.ready.poke(false.B)
          dut.io.in.ready.expect(false.B)
        }
      }
    }

    s"HammingDecoderDecoupled ($code)" should "pass valid and ready through and decode like the model" in {
      test(new HammingDecoderDecoupled(code)) { dut =>
        for (data <- Seq(0x00, 0x69, 0xff); flip <- -1 until code.codeWidth) {
          val clean = HammingModel.encode(code, data)
          val word = if (flip < 0) clean else clean.flipBit(flip)
          val expected = HammingModel.decode(code, word)

          dut.io.in.bits.poke(word.U)
          dut.io.out.ready.poke(true.B)

          dut.io.in.valid.poke(false.B)
          dut.io.out.valid.expect(false.B)

          dut.io.in.valid.poke(true.B)
          dut.io.out.valid.expect(true.B)
          dut.io.in.ready.expect(true.B)
          dut.io.out.bits.data.expect(expected.data.U)
          dut.io.out.bits.corrected.expect(expected.corrected.B)
          dut.io.out.bits.uncorrectable.expect(expected.uncorrectable.B)
          dut.io.out.bits.syndrome.expect(expected.syndrome.U)

          dut.io.out.ready.poke(false.B)
          dut.io.in.ready.expect(false.B)
        }
      }
    }
  }
}
