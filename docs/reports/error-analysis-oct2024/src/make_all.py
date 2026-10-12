import csv, os, sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

# args: CSV, out_prefix, subtitle
CSVF   = sys.argv[1]
PREFIX = sys.argv[2]
SUB    = sys.argv[3]
HERE = os.path.dirname(os.path.abspath(__file__))

rows = []
with open(CSVF) as f:
    for r in csv.DictReader(f):
        rows.append(r)

hrs  = [float(r["hours_from_start"]) for r in rows]
dpos = [float(r["dPos_m"]) / 1000.0 for r in rows]
dvel = [float(r["dVel_ms"]) for r in rows]
rms_p = (sum(p*p for p in dpos) / len(dpos))**0.5
rms_v = (sum(v*v for v in dvel) / len(dvel))**0.5

def errplot(y, color, titleformula, ylab, fname, rms):
    fig, ax = plt.subplots(figsize=(10, 5.2))
    ax.plot(hrs, y, color=color, lw=1.6)
    ax.set_title(titleformula + "\n" + SUB, fontsize=11)
    ax.set_xlabel("time from 2024-10-07 21:59:42 UTC  (hours)")
    ax.set_ylabel(ylab)
    ax.grid(True, ls=":", alpha=0.6)
    ax.axhline(rms, color="#7f8c8d", ls="--", lw=1,
               label=f"RMS = {rms:,.1f}   max = {max(y):,.1f}")
    ax.legend(loc="lower right", fontsize=9)
    ax.set_ylim(0, max(y)*1.08)
    fig.tight_layout()
    fig.savefig(os.path.join(HERE, fname), dpi=150)
    plt.close(fig)

errplot(dpos, "#c0392b",
        r"Position error  $\sqrt{(x-x_1)^2+(y-y_1)^2+(z-z_1)^2}$",
        "position error  (km)", f"{PREFIX}_position_error.png", rms_p)
errplot(dvel, "#2471a3",
        r"Velocity error  $\sqrt{(v_x-v_{x1})^2+(v_y-v_{y1})^2+(v_z-v_{z1})^2}$",
        "velocity error  (m/s)", f"{PREFIX}_velocity_error.png", rms_v)

# ---- hourly table (md + csv) ----
sample = [r for i, r in enumerate(rows) if i % 60 == 0]
if sample[-1] is not rows[-1]:
    sample.append(rows[-1])

def fmt(v, d=3):
    return f"{float(v):,.{d}f}"

md = [f"# Prediction vs. actual — six state elements (ITRF)\n", SUB + "\n",
      "| Epoch (UTC) | Source | X (m) | Y (m) | Z (m) | VX (m/s) | VY (m/s) | VZ (m/s) |",
      "|---|---|---:|---:|---:|---:|---:|---:|"]
for r in sample:
    ep = r["epoch_utc"].replace("T", " ")
    md.append(f"| {ep} | **model** | {fmt(r['model_x_m'])} | {fmt(r['model_y_m'])} | {fmt(r['model_z_m'])} "
              f"| {fmt(r['model_vx_ms'],4)} | {fmt(r['model_vy_ms'],4)} | {fmt(r['model_vz_ms'],4)} |")
    md.append(f"|  | actual | {fmt(r['actual_x_m'])} | {fmt(r['actual_y_m'])} | {fmt(r['actual_z_m'])} "
              f"| {fmt(r['actual_vx_ms'],4)} | {fmt(r['actual_vy_ms'],4)} | {fmt(r['actual_vz_ms'],4)} |")
with open(os.path.join(HERE, f"{PREFIX}_comparison_table.md"), "w") as f:
    f.write("\n".join(md) + "\n")

with open(os.path.join(HERE, f"{PREFIX}_comparison_table_hourly.csv"), "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["epoch_utc", "source", "x_m", "y_m", "z_m", "vx_ms", "vy_ms", "vz_ms", "dPos_m", "dVel_ms"])
    for r in sample:
        ep = r["epoch_utc"]
        w.writerow([ep, "model", r["model_x_m"], r["model_y_m"], r["model_z_m"],
                    r["model_vx_ms"], r["model_vy_ms"], r["model_vz_ms"], r["dPos_m"], r["dVel_ms"]])
        w.writerow([ep, "actual", r["actual_x_m"], r["actual_y_m"], r["actual_z_m"],
                    r["actual_vx_ms"], r["actual_vy_ms"], r["actual_vz_ms"], "", ""])

# ---- table PNG (every 2 h) ----
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
fig, ax = plt.subplots(figsize=(13, 0.44*len(cells)+1.6))
ax.axis("off")
ax.set_title(SUB + "  —  six elements: model vs. actual (every 2 h)", fontsize=11, pad=16)
t = ax.table(cellText=cells, rowLabels=rowlabels, colLabels=col,
             cellLoc="right", rowLoc="center", loc="center")
t.auto_set_font_size(False); t.set_fontsize(8); t.scale(1, 1.5)
for i, c in enumerate(rowcolors):
    for j in range(len(col)):
        t[(i+1, j)].set_facecolor(c)
fig.tight_layout()
fig.savefig(os.path.join(HERE, f"{PREFIX}_comparison_table.png"), dpi=150, bbox_inches="tight")
plt.close(fig)

print(f"{PREFIX}: pos RMS={rms_p:.3f} km (max {max(dpos):.3f}), vel RMS={rms_v:.3f} m/s (max {max(dvel):.3f})")
print(f"wrote {PREFIX}_position_error.png, {PREFIX}_velocity_error.png, {PREFIX}_comparison_table.png/.md/.csv")
