package ecc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.Random

/** Software reference model of the Hamming code.
  *
  * It uses a different formulation than the hardware: the XOR of the positions
  * of all set bits in a code word is its syndrome. For encoding, the check bits
  * are chosen such that this XOR becomes zero.
  */
/** Result of decoding a code word.
  *
  * @param data          the (corrected) data word
  * @param corrected     exactly one bit was flipped and has been fixed
  * @param uncorrectable two bits were flipped (SECDED only)
  * @param syndrome      position of the flipped bit (0 = no error)
  */
case class DecodeResult(data: BigInt, corrected: Boolean, uncorrectable: Boolean = false, syndrome: Int = 0)

object HammingModel {

  private def setPositions(word: BigInt, width: Int): Seq[Int] =
    (1 to width).filter(p => word.testBit(p - 1))

  def encode(code: HammingCode, data: BigInt): BigInt = {
    // place the data bits
    val withData = code.dataPositions.zipWithIndex.foldLeft(BigInt(0)) { case (w, (pos, i)) =>
      if (data.testBit(i)) w.setBit(pos - 1) else w
    }
    // the check bits are the binary representation of the XOR of all data positions
    val checks = setPositions(withData, code.hammingWidth).foldLeft(0)(_ ^ _)
    val hamming = code.checkPositions.zipWithIndex.foldLeft(withData) { case (w, (pos, i)) =>
      if (((checks >> i) & 1) == 1) w.setBit(pos - 1) else w
    }
    // SECDED: the overall parity bit on top makes the number of ones even
    if (code.secded && hamming.bitCount % 2 == 1) hamming.setBit(code.hammingWidth) else hamming
  }

  /** Decodes a code word, see [[DecodeResult]] */
  def decode(code: HammingCode, codeword: BigInt): DecodeResult = {
    val syndrome = setPositions(codeword, code.codeWidth).foldLeft(0)(_ ^ _)
    val fixed = if (syndrome >= 1 && syndrome <= code.codeWidth) codeword.flipBit(syndrome - 1) else codeword
    val data = code.dataPositions.zipWithIndex.foldLeft(BigInt(0)) { case (d, (pos, i)) =>
      if (fixed.testBit(pos - 1)) d.setBit(i) else d
    }
    DecodeResult(data, corrected = syndrome != 0, syndrome = syndrome)
  }
}

/** Encoder and decoder connected through a "noisy channel" which flips the bits
  * set in `errorMask`. Only used for testing.
  */
class HammingHarness(code: HammingCode) extends Module {
  val io = IO(new Bundle {
    val data = Input(UInt(code.dataWidth.W))
    val errorMask = Input(UInt(code.codeWidth.W))
    val codeword = Output(UInt(code.codeWidth.W)) // encoder output
    val decoded = Output(UInt(code.dataWidth.W))
    val corrected = Output(Bool())
  })
  val enc = Module(new HammingEncoder(code))
  val dec = Module(new HammingDecoder(code))
  enc.io.data := io.data
  io.codeword := enc.io.codeword
  dec.io.codeword := enc.io.codeword ^ io.errorMask
  io.decoded := dec.io.data
  io.corrected := dec.io.corrected
}

class HammingCodeTest extends AnyFlatSpec {

  "HammingCode" should "use the minimal number of check bits" in {
    // well known Hamming code sizes: data width -> code width
    val known = Seq(1 -> 3, 4 -> 7, 8 -> 12, 11 -> 15, 16 -> 21, 26 -> 31, 32 -> 38, 57 -> 63, 64 -> 71)
    for ((k, n) <- known) assert(HammingCode(k).codeWidth == n, s"for $k data bits")
  }

  it should "add one overall parity bit for SECDED" in {
    val known = Seq(1 -> 4, 8 -> 13, 16 -> 22, 32 -> 39, 64 -> 72)
    for ((k, n) <- known) {
      val code = HammingCode(k, secded = true)
      assert(code.codeWidth == n, s"for $k data bits")
      assert(code.hammingWidth == HammingCode(k).codeWidth)
    }
  }

  it should "place check bits at powers of two" in {
    val code = HammingCode(4)
    assert(code.checkPositions == Seq(1, 2, 4))
    assert(code.dataPositions == Seq(3, 5, 6, 7))
  }

  "HammingModel" should "correct every single bit error" in {
    val code = HammingCode(8)
    for (data <- 0 until 256; flip <- 0 until code.codeWidth) {
      val cw = HammingModel.encode(code, data)
      assert(HammingModel.decode(code, cw) == DecodeResult(data, corrected = false))
      // the syndrome is the position of the flipped bit (positions start at 1)
      assert(HammingModel.decode(code, cw.flipBit(flip)) == DecodeResult(data, corrected = true, syndrome = flip + 1))
    }
  }
}

class HammingTest extends AnyFlatSpec with ChiselScalatestTester {

  val rnd = new Random(42)

  /** Test data: corner cases plus random values (all values for small widths) */
  def testData(width: Int): Seq[BigInt] =
    if (width <= 8) (0 until (1 << width)).map(BigInt(_))
    else Seq(BigInt(0), (BigInt(1) << width) - 1) ++ Seq.fill(30)(BigInt(width, rnd))

  def checkWidth(width: Int): Unit = {
    val code = HammingCode(width)
    test(new HammingHarness(code)) { dut =>
      for (data <- testData(width)) {
        dut.io.data.poke(data.U)
        val expected = HammingModel.encode(code, data)

        // no error: encoder matches the model, data passes through unchanged
        dut.io.errorMask.poke(0.U)
        dut.io.codeword.expect(expected.U, s"encoding 0x${data.toString(16)}")
        dut.io.decoded.expect(data.U)
        dut.io.corrected.expect(false.B)

        // every single bit flip gets corrected
        for (bit <- 0 until code.codeWidth) {
          dut.io.errorMask.poke((BigInt(1) << bit).U)
          dut.io.decoded.expect(data.U, s"data 0x${data.toString(16)} with bit $bit flipped")
          dut.io.corrected.expect(true.B)
        }
      }
    }
  }

  // the generator works for any width, so test a range of them, including
  // widths where the code is "perfect" (4, 11, 26, 57) and where it is not
  for (w <- Seq(1, 4, 8, 11, 16, 26, 32, 57, 64))
    "Hamming generator" should s"encode and correct single bit errors for $w data bits" in checkWidth(w)
}
