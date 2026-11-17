package ecc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/** The decoder with the optional register stage. Random stalls are tested separately. */
class PipelineTest extends AnyFlatSpec with ChiselScalatestTester {

  for (secded <- Seq(false, true)) {
    val code = HammingCode(8, secded)

    /** A code word that has one bit flipped, so that the decoder has work to do */
    def damaged(data: Int): BigInt = HammingModel.encode(code, data).flipBit(2)

    def expectDecoded(dut: HammingDecoderDecoupled, data: Int): Unit = {
      dut.io.out.valid.expect(true.B)
      dut.io.out.bits.data.expect(data.U)
      dut.io.out.bits.corrected.expect(true.B)
    }

    s"HammingDecoderDecoupled with pipeline ($code)" should "deliver the result one cycle later" in {
      test(new HammingDecoderDecoupled(code, pipeline = true)) { dut =>
        dut.io.out.ready.poke(true.B)
        dut.io.in.valid.poke(false.B)
        dut.io.out.valid.expect(false.B)

        dut.io.in.bits.poke(damaged(0x69).U)
        dut.io.in.valid.poke(true.B)
        dut.io.out.valid.expect(false.B) // not yet, the word is only entering the register
        dut.clock.step()

        dut.io.in.valid.poke(false.B)
        expectDecoded(dut, 0x69) // now it is in the register
        dut.clock.step()
        dut.io.out.valid.expect(false.B) // and gone after it was taken
      }
    }

    it should "keep a word while the receiver is not ready and not lose or repeat any" in {
      test(new HammingDecoderDecoupled(code, pipeline = true)) { dut =>
        dut.io.out.ready.poke(false.B)

        // first word moves into the register
        dut.io.in.bits.poke(damaged(0x11).U)
        dut.io.in.valid.poke(true.B)
        dut.io.in.ready.expect(true.B)
        dut.clock.step()

        // register is full and the receiver waits: the sender is stopped, the word stays
        dut.io.in.bits.poke(damaged(0x22).U)
        dut.io.in.ready.expect(false.B)
        expectDecoded(dut, 0x11)
        dut.clock.step(3)
        dut.io.in.ready.expect(false.B)
        expectDecoded(dut, 0x11)

        // receiver is ready: the first word leaves while the second one enters
        dut.io.out.ready.poke(true.B)
        dut.io.in.ready.expect(true.B)
        expectDecoded(dut, 0x11)
        dut.clock.step()
        dut.io.in.valid.poke(false.B)
        expectDecoded(dut, 0x22)
        dut.clock.step()
        dut.io.out.valid.expect(false.B)
      }
    }

    it should "accept a new word every cycle when the receiver is ready" in {
      test(new HammingDecoderDecoupled(code, pipeline = true)) { dut =>
        val words = Seq(0x01, 0x23, 0x45, 0x67, 0x89, 0xab)
        dut.io.out.ready.poke(true.B)
        dut.io.in.valid.poke(true.B)

        // one extra cycle at the end to push the last word out of the register
        for (cycle <- 0 to words.length) {
          if (cycle > 0) expectDecoded(dut, words(cycle - 1))
          if (cycle < words.length) {
            dut.io.in.ready.expect(true.B)
            dut.io.in.bits.poke(damaged(words(cycle)).U)
          } else dut.io.in.valid.poke(false.B)
          dut.clock.step()
        }
        dut.io.out.valid.expect(false.B)
      }
    }
  }

  "HammingDecoderDecoupled without pipeline" should "not delay the result" in {
    val code = HammingCode(8)
    test(new HammingDecoderDecoupled(code)) { dut =>
      dut.io.out.ready.poke(true.B)
      dut.io.in.bits.poke(HammingModel.encode(code, 0x69).U)
      dut.io.in.valid.poke(true.B)
      dut.io.out.valid.expect(true.B)
      dut.io.out.bits.data.expect(0x69.U)
    }
  }
}
