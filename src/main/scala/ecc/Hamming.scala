package ecc

import chisel3._
import chisel3.util._

/** Adds the check bits to a data word.
  *
  * Each check bit is the XOR (even parity) of the data bits it covers.
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
  io.codeword := VecInit((1 to code.codeWidth).map(p => dataAt.getOrElse(p, checkAt(p)))).asUInt
}

/** Corrects a single flipped bit and extracts the data word.
  *
  * The syndrome is the XOR of the covered bits for each check bit. It is zero for
  * a correct code word and otherwise the position of the flipped bit.
  */
class HammingDecoder(val code: HammingCode) extends Module {
  override def desiredName = s"HammingDecoder${code.dataWidth}"

  val io = IO(new Bundle {
    val codeword = Input(UInt(code.codeWidth.W))
    val data = Output(UInt(code.dataWidth.W))
    val corrected = Output(Bool()) // a bit was flipped and has been fixed
  })

  private def bitAt(pos: Int): Bool = io.codeword(pos - 1)

  val syndrome = VecInit(Seq.tabulate(code.checkWidth)(i => code.coveredBy(i).map(bitAt).reduce(_ ^ _))).asUInt

  // one-hot mask of the bit to flip; a syndrome of 0 (or beyond the code word,
  // only possible with multi-bit errors) flips nothing
  val flipMask = UIntToOH(syndrome, code.codeWidth + 1)(code.codeWidth, 1)
  val fixed = io.codeword ^ flipMask

  io.data := VecInit(code.dataPositions.map(p => fixed(p - 1))).asUInt
  io.corrected := syndrome =/= 0.U
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
