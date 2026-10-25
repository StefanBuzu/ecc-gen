# Product Backlog

Ordered by priority. Items move to a sprint at sprint planning (start of each
sprint). At the sprint review (end of each sprint) we tick off what is done and
move unfinished items back to the backlog.

- Team: Stefan and Igor
- Sprint length: 2 weeks, weekly meeting
- Size: **S** = a few hours, **M** = about a day, **L** = several days
- Dates are approximate (week 13 = final presentation and demo, the exam)

One person writes the hardware and the other one writes the tests for it, so
every feature is checked by someone who did not write it.

## Sprint 1 (MVP), done

- [x] Code layout for any data width (`HammingCode`)
- [x] Generated Hamming SEC encoder and decoder
- [x] Software reference model
- [x] Tests for many widths, all single bit errors corrected
- [x] Verilog generation from the command line
- [x] CI with GitHub Actions

## MVP presentation (next 2 days)

**Stefan:**
- [ ] 0.1 Fill in the team names in the README (S)
- [ ] 0.2 Add our group (topic + repo link) to the course Google Doc (S)
- [ ] 0.3 Slides: the problem (bits flip), how a Hamming code works with a small 4-bit example, what our generator does (M)

**Igor:**
- [ ] 0.4 Accept the GitHub invite, clone the repo, run `sbt test` and `sbt run` (S)
- [ ] 0.5 Slides: demo (tests running, generated Verilog), how we test, backlog and plan (M)

**Together:**
- [ ] 0.6 Practice the presentation once (S)

## Sprint 2 (about weeks 8–9): SECDED and error outputs

**Stefan** (hardware):
- [x] 1 SECDED layout: `secded` parameter in `HammingCode`, extra overall parity bit as MSB (S).
  Done when the code width is 13 for 8 data bits and 72 for 64 data bits.
- [x] 2 SECDED encoder: add the overall parity bit (S)
- [ ] 3 Decoder error outputs: `corrected`, `uncorrectable`, `syndrome` (error position) (M)
- [ ] 4 README: explain SECDED and the decoder outputs, add a figure (SVG) (M)

**Igor** (tests):
- [ ] 5 Reference model: SECDED encode and decode in the test model (M)
- [ ] 6 Tests: every single bit flip is corrected, every double flip is reported as uncorrectable (M)
- [ ] 7 Verilog generator: generate both the SEC and the SECDED variant (S)

**Together:**
- [ ] 8 Sprint review: update this file, short notes on what went well and badly (S)

## Sprint 3 (about weeks 10–11): interfaces and memory, intermediate presentation

**Stefan:**
- [ ] 9 Ready/valid interface: `DecoupledIO` versions of the encoder and decoder (M)
- [ ] 10 Pipeline option: parameter to add a register stage in the decoder (M)
- [ ] 11 Tests for Igor's ECC memory (12, 13): write, inject 1 or 2 errors, read, check data and error flags (M)

**Igor:**
- [ ] 12 ECC memory: memory with encoder on write and decoder on read, depth and width as parameters (L)
- [ ] 13 Error injection: input that flips chosen bits of a stored word (S)
- [ ] 14 Tests for Stefan's ready/valid and pipeline (9, 10): random stalls (`ready` low), check no data is lost or duplicated (M)

**Together:**
- [ ] 15 Intermediate presentation and demo (M)

## Sprint 4 (about weeks 12–13): results and final exam

**Stefan:**
- [ ] 16 Formal verification: prove every single error is corrected for a small width (L, optional)

**Igor:**
- [ ] 17 Resource comparison: synthesize several widths (SEC vs SECDED), collect LUTs and timing (M)
- [ ] 18 Chart of the results (SVG) in the README (S)

**Together:**
- [ ] 19 Final README: usage, figures, results, AI usage, check all links (M)
- [ ] 20 Final slides and demo, practice (M)

## Stretch (only if time is left)

- FPGA demo: flip bits with switches, show corrected data and error LEDs
- Other codes, e.g. Hsiao SECDED (balanced XOR trees, less hardware)
