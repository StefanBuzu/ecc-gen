package ecc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.Random

/** The decoder must give the same four outputs as [[HammingModel]] for damaged
  * code words, not only for the single flips tested elsewhere.
  */
class DecoderModelTest extends AnyFlatSpec with ChiselScalatestTester {

  def expectSame(dut: HammingDecoder, code: HammingCode, word: BigInt, what: String): Unit = {
    val m = HammingModel.decode(code, word)
    dut.io.codeword.poke(word.U)
    dut.io.data.expect(m.data.U, what)
    dut.io.corrected.expect(m.corrected.B, what)
    dut.io.uncorrectable.expect(m.uncorrectable.B, what)
    dut.io.syndrome.expect(m.syndrome.U, what)
  }

  for (secded <- Seq(false, true); width <- Seq(4, 8, 11)) {
    val code = HammingCode(width, secded)

    s"HammingDecoder ($code)" should "match the model for every pair of flipped bits" in {
      val rnd = new Random(width)
      test(new HammingDecoder(code)) { dut =>
        for (data <- Seq(BigInt(0), (BigInt(1) << width) - 1, BigInt(width, rnd)); a <- 0 until code.codeWidth; b <- a + 1 until code.codeWidth) {
          val word = HammingModel.encode(code, data).flipBit(a).flipBit(b)
          expectSame(dut, code, word, s"data 0x${data.toString(16)}, bits $a and $b flipped")
        }
      }
    }
  }
}
