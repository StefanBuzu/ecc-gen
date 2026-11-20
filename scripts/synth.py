#!/usr/bin/env python3
"""Measure the resources and the speed of the generated encoders and decoders.

Run `sbt "runMain ecc.SynthGen"` first, it writes the Verilog to generated/synth.
Needs the open source tools from `pip install yowasp-yosys yowasp-nextpnr-ice40`
(or Yosys and nextpnr-ice40 on the PATH). Only the standard library is used here.

For every width, variant (SEC, SECDED) and module (encoder, decoder) it runs

  * Yosys `synth_ice40`   -> number of 4-input LUTs and flip-flops (Lattice iCE40)
  * nextpnr-ice40         -> maximum clock frequency after place and route
  * Yosys `synth_xilinx`  -> number of LUTs (up to 6 inputs) for Xilinx 7 series

and writes results/resources.csv.
"""
import csv
import json
import re
import shutil
import subprocess
import sys
import sysconfig
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SYNTH = ROOT / "generated" / "synth"
WORK = SYNTH / "work"
OUT = ROOT / "results" / "resources.csv"
WIDTHS = [8, 16, 32, 64]


def tool(name):
    """Find yosys or nextpnr, installed by pip (yowasp-*) or on the PATH"""
    for candidate in (name, "yowasp-" + name):
        found = shutil.which(candidate)
        if found:
            return found
        exe = Path(sysconfig.get_path("scripts")) / (candidate + (".exe" if sys.platform == "win32" else ""))
        if exe.exists():
            return str(exe)
    sys.exit(f"{name} not found, install it with: pip install yowasp-yosys yowasp-nextpnr-ice40")


YOSYS = tool("yosys")
NEXTPNR = tool("nextpnr-ice40")


def yosys(script):
    run = subprocess.run([YOSYS, "-q", "-p", script], cwd=WORK, capture_output=True, text=True)
    if run.returncode != 0:
        sys.exit(f"yosys failed for: {script}\n{run.stdout[-1500:]}{run.stderr[-1500:]}")


def cell_counts(stat_file):
    stat = json.loads((WORK / stat_file).read_text())
    return stat["design"]["num_cells_by_type"]


def ice40(verilog, top):
    """LUT4 and flip-flop count, and maximum frequency in MHz"""
    yosys(f"read_verilog -sv {verilog}; synth_ice40 -top {top} -json {top}.json; tee -q -o stat_ice40.json stat -json")
    cells = cell_counts("stat_ice40.json")
    luts = cells.get("SB_LUT4", 0)
    ffs = sum(n for c, n in cells.items() if c.startswith("SB_DFF"))

    # place and route; IO pins are not constrained, only the register-to-register path counts
    run = subprocess.run(
        [NEXTPNR, "--hx8k", "--package", "ct256", "--json", f"{top}.json", "--freq", "100", "--seed", "1"],
        cwd=WORK, capture_output=True, text=True,
    )
    log = run.stdout + run.stderr
    freqs = re.findall(r"Max frequency for clock\s+'[^']*':\s+([\d.]+) MHz", log)
    if not freqs:
        sys.exit(f"nextpnr gave no frequency for {top}:\n{log[-1500:]}")
    return luts, ffs, float(freqs[-1])


def xilinx(verilog, top):
    """Number of LUTs (1 to 6 inputs) for Xilinx 7 series"""
    yosys(f"read_verilog -sv {verilog}; synth_xilinx -family xc7 -top {top}; tee -q -o stat_xc7.json stat -json")
    cells = cell_counts("stat_xc7.json")
    return sum(n for c, n in cells.items() if re.fullmatch(r"LUT[1-6]", c))


def main():
    WORK.mkdir(parents=True, exist_ok=True)
    rows = []
    for width in WIDTHS:
        for variant in ("sec", "secded"):
            for kind, prefix in (("encoder", "EncoderSynth"), ("decoder", "DecoderSynth")):
                top = f"{prefix}{width}"
                verilog = SYNTH / variant / f"{top}.sv"
                if not verilog.exists():
                    sys.exit(f"{verilog} is missing, run: sbt \"runMain ecc.SynthGen\"")
                rel = f"../{variant}/{top}.sv"  # the tools run in the work directory, avoids spaces in paths
                luts4, ffs, fmax = ice40(rel, top)
                luts6 = xilinx(rel, top)
                rows.append(dict(width=width, variant=variant.upper(), module=kind,
                                 ice40_lut4=luts4, ice40_ff=ffs, ice40_fmax_mhz=round(fmax, 1), xc7_lut=luts6))
                print(f"{variant.upper():7} {kind:8} {width:3} bits: {luts4:4} LUT4, {ffs:3} FF, {fmax:6.1f} MHz, {luts6:4} LUT (xc7)", flush=True)
    OUT.parent.mkdir(exist_ok=True)
    with OUT.open("w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)
    print("written", OUT)


if __name__ == "__main__":
    main()
