#!/usr/bin/env python3
"""Draw docs/resources.svg from results/resources.csv (standard library only).

Four panels: LUT4 and Fmax, for the encoder and for the decoder, SEC next to
SECDED for each data width. Run `python scripts/synth.py` first for the data.
"""
import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CSV = ROOT / "results" / "resources.csv"
OUT = ROOT / "docs" / "resources.svg"

SEC_COLOR = "#2f6db5"  # blue
SECDED_COLOR = "#e07b20"  # orange (distinguishable also with color blindness)
TEXT = "#1a1a1a"
GRID = "#d0d0d0"

PANEL_W, PANEL_H = 420, 250
LEFT, TOP = 55, 40  # room for the axis labels and the panel title inside a panel
PLOT_W, PLOT_H = PANEL_W - LEFT - 15, PANEL_H - TOP - 50


def nice_max(value):
    """A round upper limit for the axis, and the tick step"""
    for step in (1, 2, 5, 10, 20, 25, 50, 100, 200):
        if value / step <= 5:
            return step * (int(value / step) + 1), step
    return value, value


def panel(x0, y0, title, unit, widths, sec, secded):
    top, step = nice_max(max(sec + secded))
    out = [f'<g transform="translate({x0},{y0})">']
    out.append(f'<text x="{LEFT}" y="22" font-size="15" font-weight="bold" fill="{TEXT}">{title}</text>')
    out.append(f'<text x="{PANEL_W - 15}" y="22" font-size="12" text-anchor="end" fill="#555">{unit}</text>')
    # grid and y axis
    for tick in range(0, top + 1, step):
        y = TOP + PLOT_H - PLOT_H * tick / top
        out.append(f'<line x1="{LEFT}" y1="{y:.1f}" x2="{LEFT + PLOT_W}" y2="{y:.1f}" stroke="{GRID}" stroke-width="1"/>')
        out.append(f'<text x="{LEFT - 6}" y="{y + 4:.1f}" font-size="11" text-anchor="end" fill="{TEXT}">{tick}</text>')
    group = PLOT_W / len(widths)
    bar = group * 0.32
    for i, w in enumerate(widths):
        cx = LEFT + group * (i + 0.5)
        for j, (value, color) in enumerate(((sec[i], SEC_COLOR), (secded[i], SECDED_COLOR))):
            h = PLOT_H * value / top
            x = cx - bar + j * bar
            out.append(f'<rect x="{x:.1f}" y="{TOP + PLOT_H - h:.1f}" width="{bar - 2:.1f}" height="{h:.1f}" fill="{color}"/>')
            out.append(f'<text x="{x + (bar - 2) / 2:.1f}" y="{TOP + PLOT_H - h - 4:.1f}" font-size="10" text-anchor="middle" fill="{TEXT}">{value:.0f}</text>')
        out.append(f'<text x="{cx:.1f}" y="{TOP + PLOT_H + 16}" font-size="12" text-anchor="middle" fill="{TEXT}">{w}</text>')
    out.append(f'<text x="{LEFT + PLOT_W / 2}" y="{TOP + PLOT_H + 34}" font-size="11" text-anchor="middle" fill="#555">data bits</text>')
    out.append(f'<line x1="{LEFT}" y1="{TOP + PLOT_H}" x2="{LEFT + PLOT_W}" y2="{TOP + PLOT_H}" stroke="{TEXT}" stroke-width="1"/>')
    out.append("</g>")
    return out


def main():
    rows = list(csv.DictReader(CSV.open()))
    widths = sorted({int(r["width"]) for r in rows})

    def series(module, column, variant):
        return [float(next(r[column] for r in rows if int(r["width"]) == w and r["module"] == module and r["variant"] == variant)) for w in widths]

    width, height = 2 * PANEL_W + 20, 2 * PANEL_H + 70
    svg = [
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" width="{width}" height="{height}" font-family="Arial, Helvetica, sans-serif">',
        f'<rect width="{width}" height="{height}" fill="#ffffff"/>',
        f'<text x="{width / 2}" y="24" font-size="18" font-weight="bold" text-anchor="middle" fill="{TEXT}">ECC generator: hardware used and speed, SEC vs SECDED</text>',
    ]
    for k, (module, column, title, unit) in enumerate((
        ("encoder", "ice40_lut4", "Encoder: logic", "LUT4 (lower is better)"),
        ("decoder", "ice40_lut4", "Decoder: logic", "LUT4 (lower is better)"),
        ("encoder", "ice40_fmax_mhz", "Encoder: speed", "Fmax in MHz (higher is better)"),
        ("decoder", "ice40_fmax_mhz", "Decoder: speed", "Fmax in MHz (higher is better)"),
    )):
        svg += panel(10 + (k % 2) * (PANEL_W + 10), 34 + (k // 2) * PANEL_H, title, unit, widths,
                     series(module, column, "SEC"), series(module, column, "SECDED"))
    # legend
    ly = height - 22
    svg.append(f'<rect x="{width / 2 - 120}" y="{ly - 11}" width="14" height="14" fill="{SEC_COLOR}"/>')
    svg.append(f'<text x="{width / 2 - 100}" y="{ly}" font-size="13" fill="{TEXT}">SEC (Hamming)</text>')
    svg.append(f'<rect x="{width / 2 + 20}" y="{ly - 11}" width="14" height="14" fill="{SECDED_COLOR}"/>')
    svg.append(f'<text x="{width / 2 + 40}" y="{ly}" font-size="13" fill="{TEXT}">SECDED</text>')
    svg.append("</svg>")
    OUT.parent.mkdir(exist_ok=True)
    OUT.write_text("\n".join(svg), encoding="utf-8")
    print("written", OUT)


if __name__ == "__main__":
    main()
