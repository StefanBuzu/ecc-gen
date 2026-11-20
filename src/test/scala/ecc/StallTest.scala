package ecc

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.Random

/** Drives a ready/valid module with random stalls on both sides.
  *
  * The sender offers `items` words and keeps each one on the interface until it is
  * accepted. The receiver is ready at random. Every word that comes out is
  * compared with the expected one, in order, so a lost or a repeated word shows up.
  */
object StallDriver {

  /** Probability that the sender has a word, and that the receiver is ready, in each cycle */
  case class Stalls(pValid: Double, pReady: Double) {
    override def toString = f"valid ${pValid * 100}%.0f%%, ready ${pReady * 100}%.0f%%"
  }

  val patterns = Seq(Stalls(1.0, 0.5), Stalls(0.5, 1.0), Stalls(0.5, 0.5), Stalls(0.9, 0.1))

  /** @param drive     puts word `i` on the input bits
    * @param outValues the current output as numbers (all fields of the output)
    * @param expected  the output that word `i` has to produce
    */
  def runWithStalls[I <: Data, O <: Data](clock: Clock, in: DecoupledIO[I], out: DecoupledIO[O], items: Int, stalls: Stalls, seed: Int)(
      drive: Int => Unit
  )(outValues: () => Seq[BigInt], expected: Int => Seq[BigInt]): Unit = {
    val rnd = new Random(seed)
    var sent = 0 // words accepted by the module
    var received = 0 // words taken from the module
    var holding = false // the sender has offered the current word and must keep it
    var cycles = 0

    while (received < items) {
      assert(cycles < items * 100, s"stuck after $cycles cycles: $sent sent, $received received")

      val offering = sent < items && (holding || rnd.nextDouble() < stalls.pValid)
      holding = offering
      val receiverReady = rnd.nextDouble() < stalls.pReady

      out.ready.poke(receiverReady.B)
      in.valid.poke(offering.B)
      if (offering) drive(sent)

      // the outputs have settled after the pokes
      val accepted = offering && in.ready.peekBoolean()
      val taken = out.valid.peekBoolean() && receiverReady

      if (taken) {
        assert(outValues() == expected(received), s"word $received is wrong (cycle $cycles, $stalls)")
        received += 1
      }
      if (accepted) sent += 1
      clock.step()
      cycles += 1
    }

    // everything has come out once: nothing more may appear
    in.valid.poke(false.B)
    out.ready.poke(true.B)
    for (_ <- 0 until 4) {
      out.valid.expect(false.B, s"a word appeared after all $items were received ($stalls)")
      clock.step()
    }
    assert(sent == items, s"sent $sent of $items words")
  }
}

/** Random stalls on the ready/valid wrappers: no word may be lost or repeated. */
class StallTest extends AnyFlatSpec with ChiselScalatestTester {
  import StallDriver._

  val items = 300

  /** The decoder output as numbers, to compare with the model */
  def decodedValues(out: DecodedWord): Seq[BigInt] =
    Seq(
      out.data.peekInt(),
      BigInt(if (out.corrected.peekBoolean()) 1 else 0),
      BigInt(if (out.uncorrectable.peekBoolean()) 1 else 0),
      out.syndrome.peekInt()
    )

  def modelValues(r: DecodeResult): Seq[BigInt] =
    Seq(r.data, BigInt(if (r.corrected) 1 else 0), BigInt(if (r.uncorrectable) 1 else 0), BigInt(r.syndrome))

  /** Code words with no error, or with one bit flipped (SECDED: sometimes two) */
  def damagedWords(code: HammingCode, n: Int, seed: Int): Seq[(BigInt, BigInt)] = {
    val rnd = new Random(seed)
    Seq.fill(n) {
      val data = BigInt(code.dataWidth, rnd)
      var word = HammingModel.encode(code, data)
      val flips = if (code.secded) rnd.nextInt(3) else rnd.nextInt(2)
      for (bit <- rnd.shuffle((0 until code.codeWidth).toList).take(flips)) word = word.flipBit(bit)
      (data, word)
    }
  }

  for (secded <- Seq(false, true); stalls <- patterns) {
    val code = HammingCode(8, secded)

    s"HammingEncoderDecoupled ($code, $stalls)" should "not lose or repeat words" in {
      val rnd = new Random(1)
      val data = Seq.fill(items)(BigInt(code.dataWidth, rnd))
      test(new HammingEncoderDecoupled(code)) { dut =>
        runWithStalls(dut.clock, dut.io.in, dut.io.out, items, stalls, seed = 2)(i => dut.io.in.bits.poke(data(i).U))(
          () => Seq(dut.io.out.bits.peekInt()),
          i => Seq(HammingModel.encode(code, data(i)))
        )
      }
    }
  }

  for (secded <- Seq(false, true); stalls <- patterns) {
    val code = HammingCode(8, secded)

    s"HammingDecoderDecoupled ($code, $stalls)" should "not lose or repeat words and decode like the model" in {
      val words = damagedWords(code, items, seed = 3)
      test(new HammingDecoderDecoupled(code)) { dut =>
        runWithStalls(dut.clock, dut.io.in, dut.io.out, items, stalls, seed = 4)(i => dut.io.in.bits.poke(words(i)._2.U))(
          () => decodedValues(dut.io.out.bits),
          i => modelValues(HammingModel.decode(code, words(i)._2))
        )
      }
    }
  }

  for (secded <- Seq(false, true); stalls <- patterns) {
    val code = HammingCode(8, secded)

    s"HammingDecoderDecoupled with pipeline ($code, $stalls)" should "not lose or repeat words and decode like the model" in {
      val words = damagedWords(code, items, seed = 5)
      test(new HammingDecoderDecoupled(code, pipeline = true)) { dut =>
        runWithStalls(dut.clock, dut.io.in, dut.io.out, items, stalls, seed = 6)(i => dut.io.in.bits.poke(words(i)._2.U))(
          () => decodedValues(dut.io.out.bits),
          i => modelValues(HammingModel.decode(code, words(i)._2))
        )
      }
    }
  }

  for (secded <- Seq(false, true); stalls <- patterns) {
    val code = HammingCode(8, secded)

    s"Encoder and decoder chained ($code, $stalls)" should "return every word once and in order" in {
      val rnd = new Random(7)
      val data = Seq.fill(items)(BigInt(code.dataWidth, rnd))
      test(new DecoupledChain(code)) { dut =>
        runWithStalls(dut.clock, dut.io.in, dut.io.out, items, stalls, seed = 8)(i => dut.io.in.bits.poke(data(i).U))(
          () => decodedValues(dut.io.out.bits),
          i => Seq(data(i), BigInt(0), BigInt(0), BigInt(0))
        )
      }
    }
  }
}
