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

hrs   = [float(r["hours_from_start"]) for r in rows]
dpos  = [float(r["dPos_m"])/1000.0 for r in rows]   # km
dvel  = [float(r["dVel_ms"]) for r in rows]          # m/s

rms_p = (sum(p*p for p in dpos)/len(dpos))**0.5
rms_v = (sum(v*v for v in dvel)/len(dvel))**0.5

# ---- position-error graph ----
fig, ax = plt.subplots(figsize=(10, 5.2))
ax.plot(hrs, dpos, color="#c0392b", lw=1.6)
ax.set_title(r"Position error  $\sqrt{(x-x_1)^2+(y-y_1)^2+(z-z_1)^2}$"
             "\nSentinel-3A: 6-day prediction (seed 2024-10-01 21:59:42 UTC) vs. actual POEORB, ITRF",
             fontsize=11)
ax.set_xlabel("time from 2024-10-07 21:59:42 UTC  (hours)")
ax.set_ylabel("position error  (km)")
ax.grid(True, ls=":", alpha=0.6)
ax.axhline(rms_p, color="#7f8c8d", ls="--", lw=1,
           label=f"RMS = {rms_p:,.1f} km   max = {max(dpos):,.1f} km")
ax.legend(loc="lower right", fontsize=9)
ax.set_ylim(0, max(dpos)*1.08)
fig.tight_layout()
fig.savefig(os.path.join(HERE, "position_error.png"), dpi=150)
plt.close(fig)

# ---- velocity-error graph ----
fig, ax = plt.subplots(figsize=(10, 5.2))
ax.plot(hrs, dvel, color="#2471a3", lw=1.6)
ax.set_title(r"Velocity error  $\sqrt{(v_x-v_{x1})^2+(v_y-v_{y1})^2+(v_z-v_{z1})^2}$"
             "\nSentinel-3A: 6-day prediction (seed 2024-10-01 21:59:42 UTC) vs. actual POEORB, ITRF",
             fontsize=11)
ax.set_xlabel("time from 2024-10-07 21:59:42 UTC  (hours)")
ax.set_ylabel("velocity error  (m/s)")
ax.grid(True, ls=":", alpha=0.6)
ax.axhline(rms_v, color="#7f8c8d", ls="--", lw=1,
           label=f"RMS = {rms_v:,.1f} m/s   max = {max(dvel):,.1f} m/s")
ax.legend(loc="lower right", fontsize=9)
ax.set_ylim(0, max(dvel)*1.08)
fig.tight_layout()
fig.savefig(os.path.join(HERE, "velocity_error.png"), dpi=150)
plt.close(fig)

print(f"rows={len(rows)}  pos RMS={rms_p:.3f} km max={max(dpos):.3f} km  "
      f"vel RMS={rms_v:.3f} m/s max={max(dvel):.3f} m/s")
print("wrote position_error.png, velocity_error.png")
