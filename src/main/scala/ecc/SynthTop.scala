package ecc

import chisel3._

/** The encoder between two register stages, for synthesis only.
  *
  * The encoder is purely combinational. To get a maximum clock frequency out of a
  * timing tool there has to be a path from a register to a register, so the
  * input and the output are registered here. The logic is the unchanged
  * [[HammingEncoder]].
  */
class EncoderSynth(val code: HammingCode) extends Module {
  override def desiredName = s"EncoderSynth${code.dataWidth}"

  val io = IO(new Bundle {
    val data = Input(UInt(code.dataWidth.W))
    val codeword = Output(UInt(code.codeWidth.W))
  })

  val enc = Module(new HammingEncoder(code))
  enc.io.data := RegNext(io.data)
  io.codeword := RegNext(enc.io.codeword)
}

/** The decoder between two register stages, for synthesis only, see [[EncoderSynth]]. */
class DecoderSynth(val code: HammingCode) extends Module {
  override def desiredName = s"DecoderSynth${code.dataWidth}"

  val io = IO(new Bundle {
    val codeword = Input(UInt(code.codeWidth.W))
    val out = Output(new DecodedWord(code))
  })

  val dec = Module(new HammingDecoder(code))
  dec.io.codeword := RegNext(io.codeword)
  io.out.data := RegNext(dec.io.data)
  io.out.corrected := RegNext(dec.io.corrected)
  io.out.uncorrectable := RegNext(dec.io.uncorrectable)
  io.out.syndrome := RegNext(dec.io.syndrome)
}

/** Generate the synthesis modules, e.g. `sbt "runMain ecc.SynthGen 8 16 32 64"`.
  *
  * SEC goes to `generated/synth/sec` and SECDED to `generated/synth/secded`. Then
  * `python scripts/synth.py` measures them.
  */
object SynthGen extends App {
  val widths = if (args.nonEmpty) args.map(_.toInt).toSeq else Seq(8, 16, 32, 64)
  for (w <- widths; secded <- Seq(false, true)) {
    val code = HammingCode(w, secded)
    val dir = s"generated/synth/${if (secded) "secded" else "sec"}"
    println(s"$code -> $dir")
    emitVerilog(new EncoderSynth(code), Array("--target-dir", dir))
    emitVerilog(new DecoderSynth(code), Array("--target-dir", dir))
  }
}
