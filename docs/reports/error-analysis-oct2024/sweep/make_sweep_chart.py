import csv, os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter

HERE = os.path.dirname(os.path.abspath(__file__))
CSV = os.path.join(HERE, "sweep_results.csv")

rows = []
with open(CSV) as f:
    lines = f.read().splitlines()
for line in lines[1:]:  # skip header; combination may contain commas
    if not line.strip():
        continue
    group, rest = line.split(",", 1)
    combination, rms = rest.rsplit(",", 1)
    rows.append({"group": group, "combination": combination,
                 "heldout_rms_m": float(rms)})

groups = {
    "ladder":   ("Cumulative ladder — marginal contribution of each force", "#2471a3"),
    "leaveout": ("Leave-one-out from the full stack — which removal hurts most", "#c0392b"),
    "atmos":    ("Atmosphere model at the full stack", "#1e8449"),
}

# ink tokens
INK = "#1a2027"; MUTED = "#6b7682"; GRID = "#e3e7eb"

def human(v):
    return f"{v/1000:,.1f} km" if v >= 1000 else f"{v:,.1f} m"

fig, axes = plt.subplots(3, 1, figsize=(11, 12),
                         gridspec_kw={"height_ratios": [10, 8, 4]})
fig.suptitle("Perturbation-model combination sweep — Sentinel-3A\n"
             "held-out prediction error (fit 6 h, predict ~20 h; lower = better)",
             fontsize=13, fontweight="bold", color=INK, y=0.985)

for ax, key in zip(axes, ["ladder", "leaveout", "atmos"]):
    title, color = groups[key]
    g = [r for r in rows if r["group"] == key]
    if key == "ladder":
        g = g  # keep ladder order (shows cumulative progression)
    else:
        g = sorted(g, key=lambda r: r["heldout_rms_m"])
    labels = [r["combination"] for r in g]
    vals = [r["heldout_rms_m"] for r in g]
    y = range(len(g))
    bars = ax.barh(y, vals, color=color, height=0.62, zorder=3)
    best = min(range(len(g)), key=lambda i: vals[i])
    bars[best].set_color("#0b6b3a" if key != "leaveout" else "#7d3c98")
    ax.set_yticks(list(y))
    ax.set_yticklabels(labels, fontsize=9, color=INK)
    ax.invert_yaxis()
    ax.set_xscale("log")
    ax.set_xlabel("held-out RMS position error  (log scale)", fontsize=9, color=MUTED)
    ax.set_title(title, fontsize=10.5, color=INK, loc="left", pad=8)
    ax.grid(True, axis="x", ls=":", color=GRID, zorder=0)
    for s in ("top", "right", "left"):
        ax.spines[s].set_visible(False)
    ax.spines["bottom"].set_color(GRID)
    ax.tick_params(length=0, colors=MUTED)
    xmax = max(vals)
    for yi, v in zip(y, vals):
        ax.text(v * 1.05, yi, human(v), va="center", ha="left",
                fontsize=8.5, color=INK)
    ax.set_xlim(min(vals) * 0.5, xmax * 3.0)

fig.tight_layout(rect=[0, 0, 1, 0.96])
out = os.path.join(HERE, "sweep_chart.png")
fig.savefig(out, dpi=150, bbox_inches="tight")
print("wrote", out)
