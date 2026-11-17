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
  */
class HammingDecoderDecoupled(val code: HammingCode) extends Module {
  override def desiredName = s"HammingDecoder${code.dataWidth}Decoupled"

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(UInt(code.codeWidth.W)))
    val out = Decoupled(new DecodedWord(code))
  })

  val dec = Module(new HammingDecoder(code))
  dec.io.codeword := io.in.bits

  io.out.bits.data := dec.io.data
  io.out.bits.corrected := dec.io.corrected
  io.out.bits.uncorrectable := dec.io.uncorrectable
  io.out.bits.syndrome := dec.io.syndrome
  io.out.valid := io.in.valid
  io.in.ready := io.out.ready
}
