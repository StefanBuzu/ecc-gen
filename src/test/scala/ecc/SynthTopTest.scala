package ecc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/** The synthesis wrappers must not change what the encoder and decoder do, only
  * delay it by the two register stages.
  */
class SynthTopTest extends AnyFlatSpec with ChiselScalatestTester {

  for (secded <- Seq(false, true)) {
    val code = HammingCode(8, secded)

    s"EncoderSynth ($code)" should "give the model's code word two cycles later" in {
      test(new EncoderSynth(code)) { dut =>
        for (data <- Seq(0x00, 0x5a, 0xff)) {
          dut.io.data.poke(data.U)
          dut.clock.step(2)
          dut.io.codeword.expect(HammingModel.encode(code, data).U)
        }
      }
    }

    s"DecoderSynth ($code)" should "give the model's result two cycles later" in {
      test(new DecoderSynth(code)) { dut =>
        for (data <- Seq(0x00, 0x5a, 0xff); flip <- 0 until code.codeWidth) {
          val word = HammingModel.encode(code, data).flipBit(flip)
          val m = HammingModel.decode(code, word)
          dut.io.codeword.poke(word.U)
          dut.clock.step(2)
          dut.io.out.data.expect(m.data.U)
          dut.io.out.corrected.expect(m.corrected.B)
          dut.io.out.uncorrectable.expect(m.uncorrectable.B)
          dut.io.out.syndrome.expect(m.syndrome.U)
        }
      }
    }
  }
}
