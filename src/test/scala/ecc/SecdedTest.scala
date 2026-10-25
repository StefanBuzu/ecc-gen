package ecc

import org.scalatest.flatspec.AnyFlatSpec

import scala.util.Random

/** Tests of the SECDED support in the software reference model. */
class SecdedModelTest extends AnyFlatSpec {

  val widths = Seq(1, 4, 8, 32, 64)
  val rnd = new Random(7)

  /** Test data: corner cases plus random values (all values for small widths) */
  def testData(width: Int): Seq[BigInt] =
    if (width <= 8) (0 until (1 << width)).map(BigInt(_))
    else Seq(BigInt(0), (BigInt(1) << width) - 1) ++ Seq.fill(20)(BigInt(width, rnd))

  "SECDED model" should "keep the Hamming code word and add an even overall parity bit" in {
    for (w <- widths) {
      val sec = HammingCode(w)
      val secded = HammingCode(w, secded = true)
      for (data <- testData(w)) {
        val cw = HammingModel.encode(secded, data)
        assert(cw.bitLength <= secded.codeWidth, s"code word too wide for $w bits")
        assert(cw.bitCount % 2 == 0, s"odd parity for 0x${data.toString(16)}, $w bits")
        val hammingMask = (BigInt(1) << sec.codeWidth) - 1
        assert((cw & hammingMask) == HammingModel.encode(sec, data), s"Hamming part differs, $w bits")
      }
    }
  }

  it should "decode an undisturbed code word without flags" in {
    val code = HammingCode(8, secded = true)
    for (data <- 0 until 256) {
      val res = HammingModel.decode(code, HammingModel.encode(code, data))
      assert(res == DecodeResult(data, corrected = false, uncorrectable = false, syndrome = 0))
    }
  }

  it should "correct a flip of the overall parity bit and report syndrome 0" in {
    val code = HammingCode(8, secded = true)
    val cw = HammingModel.encode(code, 0xa5)
    val res = HammingModel.decode(code, cw.flipBit(code.codeWidth - 1))
    assert(res == DecodeResult(0xa5, corrected = true, uncorrectable = false, syndrome = 0))
  }

  it should "flag a double bit flip as uncorrectable" in {
    val code = HammingCode(8, secded = true)
    val cw = HammingModel.encode(code, 0x3c)
    val res = HammingModel.decode(code, cw.flipBit(2).flipBit(9))
    assert(res.uncorrectable && !res.corrected)
  }
}
