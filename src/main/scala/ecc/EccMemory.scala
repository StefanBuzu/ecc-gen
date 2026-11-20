package ecc

import chisel3._
import chisel3.util._

/** A memory that stores every word together with its check bits.
  *
  * The memory is a plain word memory, `depth` words of `dataWidth` data bits. Each
  * word is stored as a Hamming code word (SEC or SECDED), so a bit that flips
  * inside the memory can be found and fixed when the word is read.
  *
  * @param depth     number of words
  * @param dataWidth width of a data word
  * @param secded    SECDED (also detects two flipped bits) instead of SEC
  */
class EccMemory(val depth: Int, val dataWidth: Int, val secded: Boolean = false) extends Module {
  require(depth > 0, "depth must be at least 1")
  val code = HammingCode(dataWidth, secded)

  override def desiredName = s"EccMemory${depth}x${dataWidth}${if (secded) "Secded" else "Sec"}"

  val addrWidth = log2Ceil(depth).max(1)

  val io = IO(new Bundle {
    val wrEn = Input(Bool())
    val wrAddr = Input(UInt(addrWidth.W))
    val wrData = Input(UInt(dataWidth.W))
    val rdAddr = Input(UInt(addrWidth.W))
    val rdData = Output(new DecodedWord(code))

    // error injection: flips the bits set in `injectMask` in the stored code word
    // at `injectAddr`, like a cosmic ray would
    val injectEn = Input(Bool())
    val injectAddr = Input(UInt(addrWidth.W))
    val injectMask = Input(UInt(code.codeWidth.W))
  })

  // every stored word is a complete code word, `codeWidth` bits wide
  val mem = Mem(depth, UInt(code.codeWidth.W))

  // write: the encoder adds the check bits, the word is stored as a code word
  val enc = Module(new HammingEncoder(code))
  enc.io.data := io.wrData
  when(io.wrEn) {
    mem.write(io.wrAddr, enc.io.codeword)
  }.elsewhen(io.injectEn) {
    // a write wins if both happen in the same cycle
    mem.write(io.injectAddr, mem.read(io.injectAddr) ^ io.injectMask)
  }

  // read: the stored code word goes through the decoder. The read is
  // combinational, the result is there in the same cycle as the address.
  val dec = Module(new HammingDecoder(code))
  dec.io.codeword := mem.read(io.rdAddr)
  io.rdData.data := dec.io.data
  io.rdData.corrected := dec.io.corrected
  io.rdData.uncorrectable := dec.io.uncorrectable
  io.rdData.syndrome := dec.io.syndrome
}

/** Generate Verilog for the SEC and the SECDED memory, e.g.
  * `sbt "runMain ecc.EccMemoryGen 64 32"` for 64 words of 32 bits (default 16 x 8).
  * The files are written to `generated/memory`.
  */
object EccMemoryGen extends App {
  val depth = if (args.length > 0) args(0).toInt else 16
  val width = if (args.length > 1) args(1).toInt else 8
  for (secded <- Seq(false, true)) {
    println(s"memory $depth x $width: ${HammingCode(width, secded)} code words")
    emitVerilog(new EccMemory(depth, width, secded), Array("--target-dir", "generated/memory"))
  }
}
