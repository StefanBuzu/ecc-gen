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
}
