package ecc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.Random

/** Helpers to use the memory from a test */
object EccMemoryDriver {
  def write(dut: EccMemory, addr: Int, data: BigInt): Unit = {
    dut.io.wrEn.poke(true.B)
    dut.io.wrAddr.poke(addr.U)
    dut.io.wrData.poke(data.U)
    dut.clock.step()
    dut.io.wrEn.poke(false.B)
  }

  /** Reads a word and checks that it comes back as written, without any flag */
  def expectClean(dut: EccMemory, addr: Int, data: BigInt): Unit = {
    dut.io.rdAddr.poke(addr.U)
    val msg = s"address $addr of ${dut.name}"
    dut.io.rdData.data.expect(data.U, msg)
    dut.io.rdData.corrected.expect(false.B, msg)
    dut.io.rdData.uncorrectable.expect(false.B, msg)
    dut.io.rdData.syndrome.expect(0.U, msg)
  }
}

class EccMemoryTest extends AnyFlatSpec with ChiselScalatestTester {
  import EccMemoryDriver._

  val rnd = new Random(5)

  for (secded <- Seq(false, true); (depth, width) <- Seq((8, 8), (16, 32))) {
    val kind = if (secded) "SECDED" else "SEC"

    s"EccMemory $depth x $width ($kind)" should "return every written word unchanged and without flags" in {
      test(new EccMemory(depth, width, secded)) { dut =>
        val words = Seq.fill(depth)(BigInt(width, rnd))
        for ((w, a) <- words.zipWithIndex) write(dut, a, w)
        for ((w, a) <- words.zipWithIndex) expectClean(dut, a, w)
      }
    }
  }

  "EccMemory" should "update a word on overwrite and leave the other words alone" in {
    test(new EccMemory(8, 8, secded = true)) { dut =>
      for (a <- 0 until 8) write(dut, a, a * 3)
      write(dut, 5, 0xc3)
      for (a <- 0 until 8) expectClean(dut, a, if (a == 5) 0xc3 else a * 3)
    }
  }

  it should "not change the memory while the write enable is low" in {
    test(new EccMemory(4, 8)) { dut =>
      write(dut, 2, 0x5a)
      dut.io.wrAddr.poke(2.U)
      dut.io.wrData.poke(0xff.U)
      dut.io.wrEn.poke(false.B)
      dut.clock.step(3)
      expectClean(dut, 2, 0x5a)
    }
  }

  it should "work for narrow and wide words and for a depth that is not a power of two" in {
    for (secded <- Seq(false, true); (depth, width) <- Seq((1, 4), (5, 1), (3, 64))) {
      test(new EccMemory(depth, width, secded)) { dut =>
        val words = Seq.fill(depth)(BigInt(width, rnd))
        for ((w, a) <- words.zipWithIndex) write(dut, a, w)
        for ((w, a) <- words.zipWithIndex) expectClean(dut, a, w)
      }
    }
  }

  it should "be generated for SEC and SECDED by EccMemoryGen" in {
    EccMemoryGen.main(Array("4", "8"))
    for (kind <- Seq("Sec", "Secded"))
      assert(java.nio.file.Files.exists(java.nio.file.Paths.get("generated", "memory", s"EccMemory4x8$kind.sv")))
  }
}
