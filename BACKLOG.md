# Product Backlog

Ordered by priority. Items move to a sprint at sprint planning.

## Sprint 1 (MVP) — done

- [x] Code layout for any data width (`HammingCode`)
- [x] Generated Hamming SEC encoder and decoder
- [x] Software reference model
- [x] Tests for many widths, all single bit errors corrected
- [x] Verilog generation from the command line
- [x] CI with GitHub Actions

## Backlog

1. **SECDED**: extra overall parity bit to also *detect* (not correct) double bit errors,
   selectable by a parameter
2. **Error outputs**: report corrected / uncorrectable errors and the error position
3. **Ready/valid interface** (`DecoupledIO`) and optional pipeline register
4. **ECC memory wrapper**: memory with encoder on write and decoder on read,
   parameterized by depth and width, with error injection for testing
5. **Formal verification**: prove that every single bit error is corrected for
   a given width (ChiselTest formal)
6. **Resource comparison**: synthesize several widths and compare area and
   timing against the plain data path
7. **FPGA demo**: inject errors with switches, show corrected data on LEDs
8. Stretch: other codes (e.g. Hsiao SECDED with balanced XOR trees)
