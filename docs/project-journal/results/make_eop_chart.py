import csv, os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))

def load(path):
    d = {}
    for line in open(path).read().splitlines()[1:]:
        if not line.strip():
            continue
        g, rest = line.split(",", 1)
        combo, rms = rest.rsplit(",", 1)
        if g == "ladder":
            d[combo] = float(rms)
    return d

no = load(os.path.join(HERE, "sweep_noeop.csv"))
yes = load(os.path.join(HERE, "sweep_realeop.csv"))
labels = list(no.keys())
nov = [no[k] for k in labels]
yev = [yes[k] for k in labels]

INK = "#1a2027"; MUTED = "#6b7682"; GRID = "#e3e7eb"
C_NO = "#9aa5b1"; C_YES = "#2471a3"

fig, ax = plt.subplots(figsize=(11, 7))
y = np.arange(len(labels))
h = 0.38
ax.barh(y + h/2, nov, height=h, color=C_NO, label="no EOP (zero polar motion)", zorder=3)
ax.barh(y - h/2, yev, height=h, color=C_YES, label="real EOP (finals2000A.all)", zorder=3)
ax.set_yticks(y); ax.set_yticklabels(labels, fontsize=9, color=INK); ax.invert_yaxis()
ax.set_xscale("log")
ax.set_xlabel("held-out prediction RMS  (m, log scale) — lower is better", fontsize=9, color=MUTED)
ax.set_title("Force-model sweep with vs. without real orekit-data EOP\n"
             "Sentinel-3A: fit 6 h, predict ~20 h, real space weather (gravity 20x20)",
             fontsize=12, fontweight="bold", color=INK)
ax.grid(True, axis="x", ls=":", color=GRID, zorder=0)
for s in ("top", "right", "left"):
    ax.spines[s].set_visible(False)
ax.spines["bottom"].set_color(GRID)
ax.tick_params(length=0, colors=MUTED)
for yi, (a, b) in enumerate(zip(nov, yev)):
    ax.text(a*1.05, yi + h/2, f"{a:,.0f}", va="center", fontsize=7.5, color=MUTED)
    ax.text(b*1.05, yi - h/2, f"{b:,.0f}", va="center", fontsize=7.5, color=INK)
ax.legend(loc="lower right", fontsize=9, frameon=False)
ax.set_xlim(min(min(nov), min(yev))*0.5, max(max(nov), max(yev))*4)
fig.tight_layout()
out = os.path.join(HERE, "eop_sweep_compare.png")
fig.savefig(out, dpi=150, bbox_inches="tight")
print("wrote", out, "| floor no-EOP=%.0f real-EOP=%.0f" % (min(nov), min(yev)))
