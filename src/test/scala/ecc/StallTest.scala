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
}
