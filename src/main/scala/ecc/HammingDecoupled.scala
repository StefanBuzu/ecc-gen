package ecc

import chisel3._
import chisel3.util._

/** What the decoder reports for one code word */
class DecodedWord(val code: HammingCode) extends Bundle {
  val data = UInt(code.dataWidth.W)
  val corrected = Bool() // a single bit was flipped and has been fixed
  val uncorrectable = Bool() // an error was detected that cannot be fixed, data is wrong
  val syndrome = UInt(code.checkWidth.W) // position of the flipped bit
}

/** [[HammingEncoder]] with ready/valid (DecoupledIO) interfaces.
  *
  * A word moves when `in.valid` and `in.ready` are both high. The encoder is
  * combinational, so it never holds a word back: `in.ready` just follows
  * `out.ready` and `out.valid` follows `in.valid`.
  */
class HammingEncoderDecoupled(val code: HammingCode) extends Module {
  override def desiredName = s"HammingEncoder${code.dataWidth}Decoupled"

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(UInt(code.dataWidth.W)))
    val out = Decoupled(UInt(code.codeWidth.W))
  })

  val enc = Module(new HammingEncoder(code))
  enc.io.data := io.in.bits

  io.out.bits := enc.io.codeword
  io.out.valid := io.in.valid
  io.in.ready := io.out.ready
}

/** [[HammingDecoder]] with ready/valid (DecoupledIO) interfaces, see
  * [[HammingEncoderDecoupled]].
  *
  * With `pipeline`, a register stage is added after the decoder. The result
  * appears one clock cycle later, but the logic behind the register starts from a
  * register instead of from the decoder's XOR trees, which allows a faster clock.
  * The register holds its word while the receiver is not ready, and a new word can
  * still enter every cycle (full throughput), so no word is lost or duplicated.
  */
class HammingDecoderDecoupled(val code: HammingCode, val pipeline: Boolean = false) extends Module {
  override def desiredName = s"HammingDecoder${code.dataWidth}Decoupled${if (pipeline) "Pipe" else ""}"

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(UInt(code.codeWidth.W)))
    val out = Decoupled(new DecodedWord(code))
  })

  val dec = Module(new HammingDecoder(code))
  dec.io.codeword := io.in.bits

  val decoded = Wire(Decoupled(new DecodedWord(code)))
  decoded.bits.data := dec.io.data
  decoded.bits.corrected := dec.io.corrected
  decoded.bits.uncorrectable := dec.io.uncorrectable
  decoded.bits.syndrome := dec.io.syndrome
  decoded.valid := io.in.valid
  io.in.ready := decoded.ready

  // pipe = true: the register accepts a new word in the same cycle as it hands
  // its word on, so a stage does not halve the throughput
  io.out <> (if (pipeline) Queue(decoded, entries = 1, pipe = true) else decoded)
}
