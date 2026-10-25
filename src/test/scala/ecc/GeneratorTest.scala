package ecc

import org.scalatest.flatspec.AnyFlatSpec

import java.nio.file.{Files, Paths}

class GeneratorTest extends AnyFlatSpec {

  "HammingGen" should "write the SEC and the SECDED variant for each width" in {
    HammingGen.main(Array("4", "8"))
    for (variant <- Seq("sec", "secded"); w <- Seq(4, 8); module <- Seq("Encoder", "Decoder")) {
      val file = Paths.get("generated", variant, s"Hamming$module$w.sv")
      assert(Files.exists(file), s"$file was not generated")
    }
  }
}
