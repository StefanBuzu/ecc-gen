# ECC Generator

A Chisel generator for error correcting code (ECC) hardware.

Memories and communication links occasionally flip bits, for example because of
radiation or noise. An error correcting code adds a few **check bits** to each
data word, so that a flipped bit can be detected and corrected when the word is
read back. This is used in server memory (ECC RAM), caches, and space hardware.

Instead of writing the encoder and decoder by hand for one data width, this
project provides a **generator**: given the data width, it computes the code
layout and produces the matching encoder and decoder circuits.

Project for the [Agile Hardware Design](https://github.com/schoeberl/agile-hw)
course (02201) at DTU.

## Status

- **Sprint 1 (MVP)**: Hamming single error correction (SEC) for any data width.
- **Sprint 2**: SECDED (also detects double bit errors) and error outputs.

| Data bits | Check bits | SEC code word | SECDED code word |
|-----------|------------|---------------|------------------|
| 8         | 4          | 12            | 13               |
| 16        | 5          | 21            | 22               |
| 32        | 6          | 38            | 39               |
| 64        | 7          | 71            | 72               |

See [BACKLOG.md](BACKLOG.md) for planned features.

## How the Hamming code works

Code bits are numbered from position 1. Check bits sit at the positions that
are powers of two (1, 2, 4, 8, ...), data bits fill the remaining positions:

```
position:  1   2   3   4   5   6   7   8   9  10  11  12
content:  c0  c1  d0  c2  d1  d2  d3  c3  d4  d5  d6  d7     (8 data bits)
```

Check bit `ci` is the parity (XOR) of all positions whose number has bit `i`
set. For example, `c0` covers positions 1, 3, 5, 7, 9, 11.

When decoding, the check bits are recomputed. The failing checks form a binary
number, the **syndrome**, which is exactly the position of the flipped bit
(0 means no error). The decoder flips that bit back and extracts the data.

The number of check bits `r` is the smallest value with `2^r >= data bits + r + 1`,
so that every position plus "no error" has its own syndrome.

## SECDED: detecting double errors

The plain Hamming code (SEC) can only handle one flipped bit. If two bits flip,
the syndrome points to a third, innocent bit, and the decoder "corrects" it into
wrong data without noticing.

SECDED (single error correction, double error detection) fixes this with one
extra **overall parity bit** `P`, stored as the most significant bit. `P` is the
XOR of all other bits, so a valid code word always has an even number of ones.
Each flipped bit changes this even/odd count:

- **one** flipped bit: the count is odd, and the syndrome says where the error is
  (syndrome 0 means only `P` itself flipped) → corrected
- **two** flipped bits: the count is even again, but the syndrome is not zero →
  reported as uncorrectable, the data is not touched

![SECDED code word and decoder decision](docs/secded.svg)

This is the code used in ECC RAM: 64 data bits are stored as 72 bits.

### Decoder outputs

| Output | Meaning |
|--------|---------|
| `data` | the data word, with a single bit error fixed |
| `corrected` | one bit was flipped and has been fixed |
| `uncorrectable` | an error was detected that cannot be fixed, `data` is wrong (two flipped bits with SECDED) |
| `syndrome` | position of the flipped bit; 0 for no error or when only the parity bit `P` flipped |

Without SECDED, `uncorrectable` is only set when the syndrome points beyond the
code word, which is a rare case of a multi-bit error.

## Usage

Requirements: Java (JDK 17 or later) and [sbt](https://www.scala-sbt.org/).

Generate Verilog for the given data widths (default 8, 32, 64). For each width
both the SEC and the SECDED variant are written, to `generated/sec/` and
`generated/secded/`:

```
sbt "run 8 16 32"
```

Run the tests:

```
sbt test
```

Use the generator in Chisel:

```scala
import ecc._

val code = HammingCode(32)                 // SEC layout for 32 data bits (38 bit code word)
val code2 = HammingCode(64, secded = true) // SECDED layout for 64 data bits (72 bit code word)

val enc = Module(new HammingEncoder(code)) // io.data -> io.codeword
val dec = Module(new HammingDecoder(code)) // io.codeword -> io.data, io.corrected,
                                           //   io.uncorrectable, io.syndrome
```

### Ready/valid interface

`HammingEncoderDecoupled` and `HammingDecoderDecoupled` wrap the modules above
with `DecoupledIO` ports (`io.in` and `io.out`). A word moves when `valid` and
`ready` are both high: the sender raises `valid` when it has data, the receiver
raises `ready` when it can take it. The decoder output is a `DecodedWord` bundle
with `data`, `corrected`, `uncorrectable` and `syndrome`.

```scala
val enc = Module(new HammingEncoderDecoupled(code)) // in: data, out: code word
val dec = Module(new HammingDecoderDecoupled(code)) // in: code word, out: DecodedWord

enc.io.out <> dec.io.in   // chain them, the handshake is connected too
```

By default the wrappers are combinational: they never hold a word back, so
`in.ready` follows `out.ready` and `out.valid` follows `in.valid`.

### Pipeline option

`HammingDecoderDecoupled(code, pipeline = true)` adds a register stage after the
decoder:

```scala
val dec = Module(new HammingDecoderDecoupled(code, pipeline = true))
```

- The result appears **one clock cycle later**.
- The XOR trees of the decoder end at a register, so whatever comes after it
  starts from a register instead of from the decoder logic. This shortens the
  longest path and allows a faster clock, at the cost of the extra cycle and
  about `dataWidth + checkWidth + 3` flip-flops (the decoded word plus a flag
  that says whether the register is full).
- The register **holds its word while `out.ready` is low** and then stops the
  sender with `in.ready = 0`. No word is lost or repeated.
- A new word can enter in the same cycle in which the old one leaves, so with a
  ready receiver the decoder still accepts **one word per cycle**.

## Structure

| File | Content |
|------|---------|
| [HammingCode.scala](src/main/scala/ecc/HammingCode.scala) | Code layout (plain Scala): number of check bits, bit positions |
| [Hamming.scala](src/main/scala/ecc/Hamming.scala) | Chisel encoder and decoder generated from the layout, Verilog emitter |
| [HammingDecoupled.scala](src/main/scala/ecc/HammingDecoupled.scala) | Ready/valid (`DecoupledIO`) wrappers for the encoder and decoder |
| [HammingTest.scala](src/test/scala/ecc/HammingTest.scala) | Reference model and tests |

The generator separates *what* the code looks like (`HammingCode`) from the
hardware that implements it (`HammingEncoder`, `HammingDecoder`).

## Results: hardware used and speed

How much logic do the encoder and decoder need, and how fast can they run? The
generated Verilog was synthesized with the open source tools Yosys and
nextpnr, no board is needed:

- **LUT4**: number of 4-input look-up tables on a Lattice iCE40 (Yosys `synth_ice40`)
- **LUT6**: number of look-up tables with up to 6 inputs on a Xilinx 7 series (Yosys `synth_xilinx`)
- **Fmax**: maximum clock frequency after place and route on an iCE40 HX8K (nextpnr)

The encoder and the decoder are purely combinational, so for the speed
measurement they are placed between two register stages (`EncoderSynth`,
`DecoderSynth`), and Fmax is the longest register-to-register path.

| Data bits | Module | LUT4 SEC | LUT4 SECDED | LUT4 extra | Fmax SEC (MHz) | Fmax SECDED (MHz) | LUT6 SEC | LUT6 SECDED |
|---|---|---|---|---|---|---|---|---|
| 8 | encoder | 6 | 9 | +50 % | 364 | 289 | 4 | 7 |
| 8 | decoder | 26 | 32 | +23 % | 235 | 226 | 26 | 25 |
| 16 | encoder | 13 | 20 | +54 % | 390 | 228 | 10 | 16 |
| 16 | decoder | 46 | 49 | +7 % | 181 | 185 | 28 | 48 |
| 32 | encoder | 32 | 36 | +12 % | 293 | 225 | 24 | 28 |
| 32 | decoder | 80 | 103 | +29 % | 146 | 120 | 68 | 94 |
| 64 | encoder | 77 | 88 | +14 % | 206 | 163 | 60 | 59 |
| 64 | decoder | 161 | 192 | +19 % | 117 | 118 | 139 | 151 |

What the numbers say:

- The **decoder is two to three times as big as the encoder**: it computes the
  syndrome, decodes it into a one-hot mask and flips the bit.
- The logic grows about **linearly with the data width** (the XOR trees), and the
  maximum clock frequency falls from about 235 MHz at 8 bits to about 117 MHz at
  64 bits, because the XOR trees get deeper.
- **SECDED costs extra**: the decoder needs about 7 to 29 % more LUT4 and the
  encoder 12 to 54 % more, for the overall parity bit and the extra decision.
  The encoder numbers are small, so a single LUT makes a large percentage.
- Treat differences of 10 to 15 % with care. Each value is **one place-and-route run
  with one seed** and unconstrained pins, so the placement adds noise (for
  example, the 8-bit SECDED decoder needs one LUT6 less than the SEC one).

To measure it yourself (needs `pip install yowasp-yosys yowasp-nextpnr-ice40`):

```
sbt "runMain ecc.SynthGen"     # Verilog for SEC and SECDED to generated/synth
python scripts/synth.py        # writes results/resources.csv and prints this table
```

## Testing

- A software **reference model** computes the code in a different way than the
  hardware (the XOR of the positions of all set bits is the syndrome), so the
  tests compare two independent implementations.
- For each tested width (1, 4, 8, 11, 16, 26, 32, 57, 64), the encoder output
  is compared with the model, and **every single bit flip** of the code word is
  checked to be corrected, with the syndrome pointing to the flipped bit. Widths
  up to 8 bits are tested exhaustively.
- For SECDED, the encoder is checked to add the correct parity bit, and the
  decoder is checked to correct every single flip and to report **every pair of
  flipped bits** as uncorrectable.
- Tests run automatically on every push with GitHub Actions
  ([ci.yml](.github/workflows/ci.yml)).

## Team

- Stefan Buzu, s260383 ([StefanBuzu](https://github.com/StefanBuzu))
- Igor Cretu ([igorcretu](https://github.com/igorcretu))

The development process (Scrum, sprints, tasks per person) is documented in
[BACKLOG.md](BACKLOG.md).

## Use of AI

We used Claude (an AI assistant) to help set up the project, implement the MVP
(generator, reference model and tests), implement the SECDED hardware in
sprint 2, write documentation, and review pull requests. We reviewed the code,
ran the tests, and made sure we understand how it works.

## License

[BSD 2-Clause](LICENSE)
