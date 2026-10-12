#!/usr/bin/env python3
"""Plots for the model-comparison report."""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import csv, os

OUT = os.path.join(os.path.dirname(__file__), "assets")
os.makedirs(OUT, exist_ok=True)
CSV = "/tmp/claude-0/-home-user-URSC/61dc98cb-9bea-52d0-a2f6-f89d8472166c/scratchpad/dsstcheck/longarc.csv"

day, cur, rev = [], [], []
with open(CSV) as f:
    for row in csv.reader(f):
        day.append(int(row[0])); cur.append(float(row[1])); rev.append(float(row[2]))

# DSST long-term: current (mean-only) vs revised (osculating), log scale, in km
fig, ax = plt.subplots(figsize=(7.2, 3.6))
ax.semilogy([d for d in day[1:]], [c/1000 for c in cur[1:]], color="#C7623B", lw=2,
            marker="o", ms=3, label="current DSST (mean-seed / mean output)")
ax.semilogy([d for d in day[1:]], [r/1000 for r in rev[1:]], color="#2F6DB5", lw=2,
            marker="s", ms=3, label="revised DSST (osculating↔mean + short-period)")
ax.set_xlabel("time since epoch (days)")
ax.set_ylabel("position error vs Cowell (km, log scale)")
ax.set_title("DSST long-term prediction: current vs revised (truth = Cowell)", fontsize=11)
ax.grid(alpha=0.25, which="both"); ax.legend(fontsize=8.5, loc="center right")
fig.tight_layout(); fig.savefig(os.path.join(OUT, "plot_dsst_longterm.png"), dpi=200); plt.close(fig)

# Cowell precise-orbit 24h: current vs revised RMS (honest: near-identical; albedo marginal)
fig, ax = plt.subplots(figsize=(6.2, 3.0))
labels = ["S3A", "S3B"]
cur24 = [165.6, 146.2]; rev24 = [164.8, 145.4]
x = range(len(labels)); w = 0.38
ax.bar([i-w/2 for i in x], cur24, w, color="#C7623B", label="current (constant SW, no albedo)")
ax.bar([i+w/2 for i in x], rev24, w, color="#2F6DB5", label="revised (constant SW + albedo)")
for i,(c,r) in enumerate(zip(cur24,rev24)):
    ax.text(i-w/2, c+1.5, f"{c:.1f}", ha="center", fontsize=8)
    ax.text(i+w/2, r+1.5, f"{r:.1f}", ha="center", fontsize=8)
ax.set_xticks(list(x)); ax.set_xticklabels(labels)
ax.set_ylabel("24 h total position RMS (m)")
ax.set_title("Cowell precise-orbit accuracy vs POEORB (real SW pending)", fontsize=11)
ax.grid(alpha=0.25, axis="y"); ax.legend(fontsize=7.5)
fig.tight_layout(); fig.savefig(os.path.join(OUT, "plot_cowell_cmp.png"), dpi=200); plt.close(fig)
print("comparison plots rendered")
