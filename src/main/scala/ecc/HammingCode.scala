package ecc

/** Layout of a Hamming code for `dataWidth` data bits.
  *
  * Code bits are numbered by their position 1 to `hammingWidth`. Check (parity)
  * bits sit at the positions that are powers of two (1, 2, 4, 8, ...), data bits
  * fill the remaining positions in order. Check bit `i` covers every position
  * whose number has bit `i` set. After a single bit flip, the check bits that
  * fail spell out the position of the flipped bit in binary (the syndrome).
  *
  * Position `p` is stored at bit `p - 1` of the code word UInt.
  *
  * With `secded`, one extra parity bit over the whole code word is stored as the
  * most significant bit. It makes the code single error correcting, double error
  * detecting (SECDED): a single flip changes the overall parity, two flips do not.
  * Without it the code is single error correcting (SEC) only.
  *
  * This class is plain Scala: it decides the structure, and the Chisel modules
  * in [[Hamming]] turn it into hardware.
  */
case class HammingCode(dataWidth: Int, secded: Boolean = false) {
  require(dataWidth >= 1, "dataWidth must be at least 1")

  /** Smallest number of check bits r with 2^r >= dataWidth + r + 1, so that every
    * position plus "no error" gets its own syndrome.
    */
  val checkWidth: Int = Iterator.from(2).find(r => (BigInt(1) << r) >= dataWidth + r + 1).get

  /** Width of the Hamming part (positions 1 to hammingWidth) */
  val hammingWidth: Int = dataWidth + checkWidth

  /** Total width, including the overall parity bit for SECDED */
  val codeWidth: Int = hammingWidth + (if (secded) 1 else 0)

  private def isPowerOfTwo(p: Int) = (p & (p - 1)) == 0

  /** Position of check bit `i` is 2^i */
  val checkPositions: Seq[Int] = Seq.tabulate(checkWidth)(1 << _)

  /** Position of data bit `i` (data bit 0 is the LSB of the data word) */
  val dataPositions: Seq[Int] = (1 to hammingWidth).filterNot(isPowerOfTwo)

  /** All positions checked by check bit `i`, including its own position */
  def coveredBy(i: Int): Seq[Int] = (1 to hammingWidth).filter(p => ((p >> i) & 1) == 1)

  override def toString: String = s"${if (secded) "SECDED" else "Hamming"}($codeWidth,$dataWidth)"
}
