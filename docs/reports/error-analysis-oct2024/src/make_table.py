import csv, os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))
CSV = os.path.join(HERE, "error_oct7.csv")

rows = []
with open(CSV) as f:
    for r in csv.DictReader(f):
        rows.append(r)

# hourly sample: one row per 60 min (grid is 60 s -> every 60th row)
sample = [r for i, r in enumerate(rows) if i % 60 == 0]
if sample[-1] is not rows[-1]:
    sample.append(rows[-1])

# ---------- markdown + csv ----------
md = []
md.append("# Prediction vs. actual — six state elements (ITRF)\n")
md.append("Satellite Sentinel-3A. Seed epoch 2024-10-01 21:59:42 UTC. "
          "My model = full-force propagation of the single seed; Actual = POEORB precise orbit. "
          "For each epoch the model's six elements are listed first, then the actual six.\n")
header = "| Epoch (UTC) | Source | X (m) | Y (m) | Z (m) | VX (m/s) | VY (m/s) | VZ (m/s) |"
sep    = "|---|---|---:|---:|---:|---:|---:|---:|"
md.append(header); md.append(sep)

csv_out = os.path.join(HERE, "comparison_table_hourly.csv")
with open(csv_out, "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["epoch_utc", "source", "x_m", "y_m", "z_m", "vx_ms", "vy_ms", "vz_ms", "dPos_m", "dVel_ms"])
    for r in sample:
        ep = r["epoch_utc"]
        w.writerow([ep, "model", r["model_x_m"], r["model_y_m"], r["model_z_m"],
                    r["model_vx_ms"], r["model_vy_ms"], r["model_vz_ms"], r["dPos_m"], r["dVel_ms"]])
        w.writerow([ep, "actual", r["actual_x_m"], r["actual_y_m"], r["actual_z_m"],
                    r["actual_vx_ms"], r["actual_vy_ms"], r["actual_vz_ms"], "", ""])

def fmt(v, d=3):
    return f"{float(v):,.{d}f}"

for r in sample:
    ep = r["epoch_utc"].replace("T", " ")
    md.append(f"| {ep} | **model** | {fmt(r['model_x_m'])} | {fmt(r['model_y_m'])} | {fmt(r['model_z_m'])} "
              f"| {fmt(r['model_vx_ms'],4)} | {fmt(r['model_vy_ms'],4)} | {fmt(r['model_vz_ms'],4)} |")
    md.append(f"|  | actual | {fmt(r['actual_x_m'])} | {fmt(r['actual_y_m'])} | {fmt(r['actual_z_m'])} "
              f"| {fmt(r['actual_vx_ms'],4)} | {fmt(r['actual_vy_ms'],4)} | {fmt(r['actual_vz_ms'],4)} |")

with open(os.path.join(HERE, "comparison_table.md"), "w") as f:
    f.write("\n".join(md) + "\n")

# ---------- PNG table (every 2 h to stay legible) ----------
sample2 = [r for i, r in enumerate(rows) if i % 120 == 0]
if sample2[-1] is not rows[-1]:
    sample2.append(rows[-1])

cells, rowlabels, rowcolors = [], [], []
col = ["X (m)", "Y (m)", "Z (m)", "VX (m/s)", "VY (m/s)", "VZ (m/s)"]
for r in sample2:
    ep = r["epoch_utc"].replace("T", "\n")
    cells.append([f"{float(r['model_x_m']):,.1f}", f"{float(r['model_y_m']):,.1f}", f"{float(r['model_z_m']):,.1f}",
                  f"{float(r['model_vx_ms']):,.2f}", f"{float(r['model_vy_ms']):,.2f}", f"{float(r['model_vz_ms']):,.2f}"])
    rowlabels.append(f"{ep}\nmodel"); rowcolors.append("#eaf2f8")
    cells.append([f"{float(r['actual_x_m']):,.1f}", f"{float(r['actual_y_m']):,.1f}", f"{float(r['actual_z_m']):,.1f}",
                  f"{float(r['actual_vx_ms']):,.2f}", f"{float(r['actual_vy_ms']):,.2f}", f"{float(r['actual_vz_ms']):,.2f}"])
    rowlabels.append("actual"); rowcolors.append("#fdf2e9")

fig, ax = plt.subplots(figsize=(13, 0.42*len(cells)+1.4))
ax.axis("off")
ax.set_title("Sentinel-3A — predicted six elements (model) vs. actual POEORB, ITRF  (every 2 h)",
             fontsize=12, pad=14)
t = ax.table(cellText=cells, rowLabels=rowlabels, colLabels=col,
             cellLoc="right", rowLoc="center", loc="center")
t.auto_set_font_size(False); t.set_fontsize(8); t.scale(1, 1.3)
for i, c in enumerate(rowcolors):
    for j in range(len(col)):
        t[(i+1, j)].set_facecolor(c)
fig.tight_layout()
fig.savefig(os.path.join(HERE, "comparison_table.png"), dpi=150, bbox_inches="tight")
plt.close(fig)

print(f"hourly rows={len(sample)}  2h rows(png)={len(sample2)}")
print("wrote comparison_table.md, comparison_table_hourly.csv, comparison_table.png")
