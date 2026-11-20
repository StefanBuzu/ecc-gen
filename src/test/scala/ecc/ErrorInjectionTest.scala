package ecc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.Random

/** Injects errors into the stored words of the ECC memory and checks what the
  * read port reports. The expected result comes from [[HammingModel]].
  */
class ErrorInjectionTest extends AnyFlatSpec with ChiselScalatestTester {
  import EccMemoryDriver._

  val rnd = new Random(9)

  for (secded <- Seq(false, true)) {
    val kind = if (secded) "SECDED" else "SEC"
    val width = 8
    val code = HammingCode(width, secded)

    s"EccMemory ($kind)" should "fix a single flipped bit at every position of the code word" in {
      test(new EccMemory(4, width, secded)) { dut =>
        for (data <- Seq(BigInt(0), BigInt(0xff), BigInt(0x5a), BigInt(width, rnd)); bit <- 0 until code.codeWidth) {
          write(dut, 1, data)
          inject(dut, 1, BigInt(1) << bit)

          val expected = HammingModel.decode(code, HammingModel.encode(code, data).flipBit(bit))
          dut.io.rdAddr.poke(1.U)
          val msg = s"data 0x${data.toString(16)}, bit $bit flipped"
          dut.io.rdData.data.expect(data.U, msg)
          dut.io.rdData.corrected.expect(true.B, msg)
          dut.io.rdData.uncorrectable.expect(false.B, msg)
          dut.io.rdData.syndrome.expect(expected.syndrome.U, msg)
        }
      }
    }
  }

  "EccMemory (SECDED)" should "report every injected double bit flip as uncorrectable" in {
    val code = HammingCode(8, secded = true)
    test(new EccMemory(2, 8, secded = true)) { dut =>
      for (data <- Seq(BigInt(0), BigInt(0xa5), BigInt(0xff)); a <- 0 until code.codeWidth; b <- a + 1 until code.codeWidth) {
        write(dut, 0, data)
        inject(dut, 0, (BigInt(1) << a) | (BigInt(1) << b))

        dut.io.rdAddr.poke(0.U)
        val msg = s"data 0x${data.toString(16)}, bits $a and $b flipped"
        dut.io.rdData.uncorrectable.expect(true.B, msg)
        dut.io.rdData.corrected.expect(false.B, msg)
      }
    }
  }

  "EccMemory" should "only change the chosen word" in {
    test(new EccMemory(8, 8, secded = true)) { dut =>
      for (a <- 0 until 8) write(dut, a, 0x10 + a)
      inject(dut, 3, 0x0004)

      for (a <- 0 until 8) {
        dut.io.rdAddr.poke(a.U)
        dut.io.rdData.data.expect((0x10 + a).U)
        dut.io.rdData.corrected.expect((a == 3).B, s"address $a")
      }
    }
  }

  it should "not change anything with an all-zero mask" in {
    test(new EccMemory(4, 8, secded = true)) { dut =>
      write(dut, 2, 0x77)
      inject(dut, 2, 0)
      expectClean(dut, 2, 0x77)
    }
  }

  it should "give a write priority over an injection in the same cycle" in {
    test(new EccMemory(4, 8, secded = true)) { dut =>
      write(dut, 1, 0x11)

      dut.io.wrEn.poke(true.B)
      dut.io.wrAddr.poke(1.U)
      dut.io.wrData.poke(0x22.U)
      dut.io.injectEn.poke(true.B)
      dut.io.injectAddr.poke(1.U)
      dut.io.injectMask.poke(0x0010.U)
      dut.clock.step()
      dut.io.wrEn.poke(false.B)
      dut.io.injectEn.poke(false.B)

      expectClean(dut, 1, 0x22) // the new word is stored without the error
    }
  }

  it should "let a later write repair an injected error" in {
    test(new EccMemory(4, 8, secded = true)) { dut =>
      write(dut, 0, 0x3c)
      inject(dut, 0, 0x0001)
      write(dut, 0, 0x3c)
      expectClean(dut, 0, 0x3c)
    }
  }
}
