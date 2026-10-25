package ecc

/** Layout of a Hamming single error correcting (SEC) code for `dataWidth` data bits.
  *
  * Code bits are numbered by their position 1 to `codeWidth`. Check (parity)
  * bits sit at the positions that are powers of two (1, 2, 4, 8, ...), data bits
  * fill the remaining positions in order. Check bit `i` covers every position
  * whose number has bit `i` set. After a single bit flip, the check bits that
  * fail spell out the position of the flipped bit in binary (the syndrome).
  *
  * Position `p` is stored at bit `p - 1` of the code word UInt.
  *
  * This class is plain Scala: it decides the structure, and the Chisel modules
  * in [[Hamming]] turn it into hardware.
  */
case class HammingCode(dataWidth: Int) {
  require(dataWidth >= 1, "dataWidth must be at least 1")

  /** Smallest number of check bits r with 2^r >= dataWidth + r + 1, so that every
    * position plus "no error" gets its own syndrome.
    */
  val checkWidth: Int = Iterator.from(2).find(r => (BigInt(1) << r) >= dataWidth + r + 1).get

  val codeWidth: Int = dataWidth + checkWidth

  private def isPowerOfTwo(p: Int) = (p & (p - 1)) == 0

  /** Position of check bit `i` is 2^i */
  val checkPositions: Seq[Int] = Seq.tabulate(checkWidth)(1 << _)

  /** Position of data bit `i` (data bit 0 is the LSB of the data word) */
  val dataPositions: Seq[Int] = (1 to codeWidth).filterNot(isPowerOfTwo)

  /** All positions checked by check bit `i`, including its own position */
  def coveredBy(i: Int): Seq[Int] = (1 to codeWidth).filter(p => ((p >> i) & 1) == 1)

  override def toString: String = s"Hamming($codeWidth,$dataWidth)"
}
