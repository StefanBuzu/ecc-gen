package ecc

import chisel3._
import chisel3.util._

/** Adds the check bits to a data word.
  *
  * Each check bit is the XOR (even parity) of the data bits it covers. For
  * SECDED, the parity of the whole Hamming part is added as the MSB.
  */
class HammingEncoder(val code: HammingCode) extends Module {
  override def desiredName = s"HammingEncoder${code.dataWidth}"

  val io = IO(new Bundle {
    val data = Input(UInt(code.dataWidth.W))
    val codeword = Output(UInt(code.codeWidth.W))
  })

  // data bit at each data position
  val dataAt: Map[Int, Bool] =
    code.dataPositions.zipWithIndex.map { case (pos, i) => pos -> io.data(i) }.toMap

  // check bit at each check position: parity of all covered data bits
  val checkAt: Map[Int, Bool] = code.checkPositions.zipWithIndex.map { case (pos, i) =>
    pos -> code.coveredBy(i).filter(_ != pos).map(dataAt).reduce(_ ^ _)
  }.toMap

  // position 1 ends up in bit 0
  val hamming = VecInit((1 to code.hammingWidth).map(p => dataAt.getOrElse(p, checkAt(p)))).asUInt

  // overall parity bit on top: makes the number of ones in the code word even
  io.codeword := (if (code.secded) Cat(hamming.xorR, hamming) else hamming)
}

/** Corrects a single flipped bit and extracts the data word.
  *
  * The syndrome is the XOR of the covered bits for each check bit. It is zero for
  * a correct code word and otherwise the position of the flipped bit.
  *
  * For SECDED, the overall parity tells a single error (parity wrong) from a
  * double error (parity right, but syndrome not zero). A double error is reported
  * as uncorrectable instead of being "corrected" into wrong data.
  */
class HammingDecoder(val code: HammingCode) extends Module {
  override def desiredName = s"HammingDecoder${code.dataWidth}"

  val io = IO(new Bundle {
    val codeword = Input(UInt(code.codeWidth.W))
    val data = Output(UInt(code.dataWidth.W))
    val corrected = Output(Bool()) // a single bit was flipped and has been fixed
    val uncorrectable = Output(Bool()) // an error was detected that cannot be fixed, data is wrong
    val syndrome = Output(UInt(code.checkWidth.W)) // position of the flipped bit (0: none or the parity bit)
  })

  private def bitAt(pos: Int): Bool = io.codeword(pos - 1)

  val syndrome = VecInit(Seq.tabulate(code.checkWidth)(i => code.coveredBy(i).map(bitAt).reduce(_ ^ _))).asUInt

  // SEC: any non-zero syndrome is taken as a single error.
  // SECDED: only an odd number of flips (wrong overall parity) is a single error.
  val parityWrong = io.codeword.xorR
  val singleError = if (code.secded) parityWrong else syndrome =/= 0.U
  val detected = if (code.secded) parityWrong || syndrome =/= 0.U else syndrome =/= 0.U
  // a syndrome beyond the code word can only come from multi-bit errors
  val corrected = singleError && syndrome <= code.hammingWidth.U

  // one-hot mask of the bit to flip; a syndrome of 0 flips nothing
  val flipMask = UIntToOH(syndrome, code.hammingWidth + 1)(code.hammingWidth, 1)
  val fixed = io.codeword(code.hammingWidth - 1, 0) ^ Mux(corrected, flipMask, 0.U)

  io.data := VecInit(code.dataPositions.map(p => fixed(p - 1))).asUInt
  io.corrected := corrected
  io.uncorrectable := detected && !corrected
  io.syndrome := syndrome
}

/** Generate Verilog for the given data widths, e.g. `sbt "run 8 32 64"` */
object HammingGen extends App {
  val widths = if (args.nonEmpty) args.map(_.toInt).toSeq else Seq(8, 32, 64)
  for (w <- widths) {
    val code = HammingCode(w)
    println(s"$code: ${code.checkWidth} check bits")
    emitVerilog(new HammingEncoder(code), Array("--target-dir", "generated"))
    emitVerilog(new HammingDecoder(code), Array("--target-dir", "generated"))
  }
}
