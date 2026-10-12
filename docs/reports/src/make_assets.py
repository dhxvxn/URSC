#!/usr/bin/env python3
"""Render equation PNGs (matplotlib mathtext, offline) and error plots for the reports."""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import os

OUT = os.path.join(os.path.dirname(__file__), "assets")
os.makedirs(OUT, exist_ok=True)

plt.rcParams["mathtext.fontset"] = "cm"   # Computer Modern look

# ---------------------------------------------------------------- equations
EQS = {
 "eom":       r"$\ddot{\vec r} \;=\; -\dfrac{\mu}{r^{3}}\,\vec r \;+\; \vec a_{\mathrm{NS}} \;+\; \vec a_{3B} \;+\; \vec a_{\mathrm{drag}} \;+\; \vec a_{\mathrm{SRP}} \;+\; \vec a_{\mathrm{tide}} \;+\; \vec a_{\mathrm{rel}}$",
 "geopot":    r"$U(r,\phi,\lambda)=\dfrac{\mu}{r}\left[1+\sum_{n=2}^{N}\sum_{m=0}^{n}\left(\dfrac{R_{\oplus}}{r}\right)^{n}\bar P_{nm}(\sin\phi)\left(\bar C_{nm}\cos m\lambda+\bar S_{nm}\sin m\lambda\right)\right]$",
 "denorm":    r"$\{\bar C_{nm},\bar S_{nm}\}=\left[\dfrac{(n+m)!}{(2-\delta_{0m})(2n+1)(n-m)!}\right]^{1/2}\{C_{nm},S_{nm}\}$",
 "thirdbody": r"$\vec a_{3B}=GM_b\left(\dfrac{\vec s-\vec r}{|\vec s-\vec r|^{3}}-\dfrac{\vec s}{|\vec s|^{3}}\right)$",
 "drag":      r"$\vec a_{\mathrm{drag}}=-\dfrac{1}{2}\,\dfrac{C_D A}{m}\,\rho\,|\vec v_{\mathrm{rel}}|\,\vec v_{\mathrm{rel}},\qquad \vec v_{\mathrm{rel}}=\vec v-\vec\omega_{\oplus}\times\vec r$",
 "srp":       r"$\vec a_{\mathrm{SRP}}=-\,\nu\,C_R\,\dfrac{A}{m}\,P_0\left(\dfrac{\mathrm{AU}}{d}\right)^{2}\hat{\vec u}_{\odot\rightarrow\mathrm{sat}}$",
 "solid":     r"$\Delta\bar C_{nm}-i\,\Delta\bar S_{nm}=\dfrac{k_{nm}}{2n+1}\sum_{b}\dfrac{GM_b}{GM_{\oplus}}\left(\dfrac{R_{\oplus}}{r_b}\right)^{n+1}\!\bar P_{nm}(\sin\phi_b)\,e^{-i m\lambda_b}$",
 "ocean":     r"$\Delta\bar C_{nm}=\sum_{f}\left[(C^{+}_{f,nm}+C^{-}_{f,nm})\cos\theta_f+(S^{+}_{f,nm}+S^{-}_{f,nm})\sin\theta_f\right]$",
 "schwarz":   r"$\vec a_{\mathrm{Schw}}=\dfrac{\mu}{c^{2}r^{3}}\left[\left(\dfrac{4\mu}{r}-v^{2}\right)\vec r+4(\vec r\cdot\vec v)\,\vec v\right]$",
 "lense":     r"$\vec a_{\mathrm{LT}}=\dfrac{2\mu}{c^{2}r^{3}}\left[\dfrac{3}{r^{2}}(\vec r\cdot\vec J)(\vec r\times\vec v)+(\vec v\times\vec J)\right]$",
 "frame":     r"$\vec r_{\mathrm{ITRF}} \;=\; W(x_p,y_p)\,R_3(\mathrm{GAST})\,N(\Delta\psi,\Delta\varepsilon)\,P(t)\,B\;\vec r_{\mathrm{GCRF}}$",
 "gmst":      r"$\theta_{\mathrm{GMST}}=280.46061837^{\circ}+360.98564736629^{\circ}\,d+3.8793\times10^{-4}\,T^{2}-\dfrac{T^{3}}{3.871\times10^{7}}$",
 "tt":        r"$\mathrm{TT}=\mathrm{TAI}+32.184\ \mathrm{s},\qquad \mathrm{UTC}=\mathrm{TAI}-\Delta AT,\qquad \mathrm{UT1}=\mathrm{UTC}+\Delta UT1$",
 "dopri":     r"$\hat{\vec y}_{k+1}=\vec y_k+h\sum_{i} b_i \vec k_i,\quad \|\vec e\|=\|\hat{\vec y}_{k+1}-\vec y_{k+1}^{*}\|,\quad h_{\mathrm{new}}=h\,S\left(\frac{\mathrm{tol}}{\|\vec e\|}\right)^{1/5}$",
 "meanrate":  r"$\left\langle\dfrac{d\mathcal{E}}{dt}\right\rangle=\dfrac{1}{2\pi}\int_{0}^{2\pi}\dfrac{\partial\mathcal{E}}{\partial\vec v}\cdot\vec\gamma\;(1-e\cos E)\,dE,\qquad \dot\lambda\rightarrow\dot\lambda+n(a)$",
 "normaleq":  r"$\left(\tilde J^{\mathrm{T}}\tilde J+\mu\,\mathrm{diag}(\tilde J^{\mathrm{T}}\tilde J)\right)\Delta\tilde{\vec p}=\tilde J^{\mathrm{T}}\vec r,\qquad \tilde J_{ij}=\dfrac{\partial \vec\rho_i}{\partial p_j}\,s_j$",
 "rms":       r"$\mathrm{RMS}=\left[\dfrac{1}{M}\sum_{i=1}^{M}|\vec r^{\,\mathrm{comp}}_i-\vec r^{\,\mathrm{obs}}_i|^{2}\right]^{1/2}$",
 "rtn":       r"$\hat{\vec R}=\dfrac{\vec r}{|\vec r|},\quad \hat{\vec C}=\dfrac{\vec r\times\vec v}{|\vec r\times\vec v|},\quad \hat{\vec A}=\hat{\vec C}\times\hat{\vec R}$",
 "along":     r"$\delta r_{\parallel}(t)\;\approx\;\frac{1}{2}\,\langle\delta a_{\parallel}\rangle\,t^{2}\;+\;\delta v_{\parallel,0}\,t$",
}

for name, tex in EQS.items():
    fig = plt.figure(figsize=(0.1, 0.1))
    t = fig.text(0, 0, tex, fontsize=17)
    fig.canvas.draw()
    bb = t.get_window_extent()
    w, h = bb.width/fig.dpi, bb.height/fig.dpi
    plt.close(fig)
    fig = plt.figure(figsize=(w+0.1, h+0.1))
    fig.text(0.02, 0.5, tex, fontsize=17, va="center")
    fig.savefig(os.path.join(OUT, f"eq_{name}.png"), dpi=200, transparent=True,
                bbox_inches="tight", pad_inches=0.02)
    plt.close(fig)
print("equations rendered:", len(EQS))

# ---------------------------------------------------------------- error data (measured)
hours = list(range(0, 25))
S3A = {  # radial, along, cross, total  (metres) at each hour, from the validate run
 "radial":[0.0,-3.8,-1.0,-1.6,-3.9,0.3,-4.5,-1.3,-1.2,-5.4,2.0,-5.9,-0.7,-1.6,-5.9,2.3,-7.4,0.3,-2.3,-6.9,3.7,-9.6,2.1,-3.5,-6.2],
 "along":[0.0,17.6,23.8,46.0,49.7,64.9,79.8,80.0,102.0,98.3,111.9,122.8,119.7,146.2,141.9,161.0,179.2,179.7,217.5,213.9,245.9,264.4,269.1,313.4,302.9],
 "cross":[-0.0,1.6,0.1,-6.6,14.0,-17.6,12.7,-0.4,-15.3,26.6,-28.4,18.9,-2.7,-12.6,20.6,-18.7,10.1,-1.0,-4.7,5.2,-2.3,-0.2,0.7,-0.1,0.5],
 "total":[0.0,18.1,23.8,46.5,51.8,67.3,80.9,80.0,103.1,102.0,115.4,124.4,119.7,146.8,143.5,162.1,179.6,179.7,217.5,214.1,246.0,264.5,269.1,313.4,302.9],
}
S3B = {
 "radial":[0.0,-3.1,-2.0,-0.8,-3.7,-0.8,-2.4,-2.0,-1.6,-2.9,-1.4,-2.9,-1.7,-1.8,-2.7,-2.3,-2.2,-2.6,-1.9,-2.0,-2.7,-1.7,-2.5,-1.9,-2.1],
 "along":[0.0,15.5,22.5,40.9,50.5,58.8,71.5,73.7,86.2,89.9,99.7,107.6,112.3,124.3,132.0,142.2,156.3,168.8,183.6,197.3,212.1,228.9,244.1,260.6,276.8],
 "cross":[-0.0,-2.1,3.2,-0.3,-6.7,14.8,-20.0,16.8,-5.5,-10.8,23.7,-27.1,20.1,-6.5,-6.7,13.9,-12.9,6.4,-0.2,-2.3,0.2,2.6,-3.6,1.8,0.8],
 "total":[0.0,16.0,22.8,41.0,51.1,60.7,74.2,75.6,86.4,90.6,102.5,111.0,114.1,124.5,132.2,142.9,156.8,168.9,183.6,197.3,212.1,228.9,244.2,260.6,276.8],
}

C_R, C_A, C_C, C_T = "#C7623B", "#2F6DB5", "#4E9B6B", "#333333"

def rtn_plot(sat, data, title, fname):
    fig, ax = plt.subplots(figsize=(7.2, 3.5))
    ax.plot(hours, data["total"], color=C_T, lw=2.2, label="total", zorder=5)
    ax.plot(hours, data["along"], color=C_A, lw=1.6, label="along-track")
    ax.plot(hours, data["radial"], color=C_R, lw=1.4, label="radial")
    ax.plot(hours, data["cross"], color=C_C, lw=1.4, label="cross-track")
    ax.axhline(0, color="#bbbbbb", lw=0.7)
    ax.set_xlabel("time since epoch (hours)")
    ax.set_ylabel("position error (m)")
    ax.set_title(title, fontsize=11)
    ax.set_xlim(0, 24); ax.grid(alpha=0.25)
    ax.legend(fontsize=8, loc="upper left", framealpha=0.9)
    fig.tight_layout()
    fig.savefig(os.path.join(OUT, fname), dpi=200)
    plt.close(fig)

rtn_plot("S3A", S3A, "Sentinel-3A — propagation error vs POEORB precise orbit (RTN)", "plot_s3a.png")
rtn_plot("S3B", S3B, "Sentinel-3B — propagation error vs POEORB precise orbit (RTN)", "plot_s3b.png")

# combined total-error comparison
fig, ax = plt.subplots(figsize=(7.2, 3.3))
ax.plot(hours, S3A["total"], color="#2F6DB5", lw=2, marker="o", ms=3, label="Sentinel-3A")
ax.plot(hours, S3B["total"], color="#C7623B", lw=2, marker="s", ms=3, label="Sentinel-3B")
ax.set_xlabel("time since epoch (hours)"); ax.set_ylabel("total position error (m)")
ax.set_title("Total position error growth (pure propagation, no fit)", fontsize=11)
ax.set_xlim(0,24); ax.grid(alpha=0.25); ax.legend(fontsize=9)
fig.tight_layout(); fig.savefig(os.path.join(OUT,"plot_total.png"), dpi=200); plt.close(fig)

# fit before/after bar chart
fig, ax = plt.subplots(figsize=(6.6, 3.0))
labels = ["S3A\nstate only","S3A\nstate+Cd","S3B\nstate only","S3B\nstate+Cd"]
pre =  [38.43,38.43,41.18,41.18]
post = [4.81,4.49,5.94,5.16]
x = range(len(labels)); w = 0.38
ax.bar([i-w/2 for i in x], pre,  w, color="#C7623B", label="pre-fit RMS")
ax.bar([i+w/2 for i in x], post, w, color="#2F6DB5", label="post-fit RMS")
for i,(a,b) in enumerate(zip(pre,post)):
    ax.text(i-w/2, a+0.6, f"{a:.1f}", ha="center", fontsize=7.5)
    ax.text(i+w/2, b+0.6, f"{b:.1f}", ha="center", fontsize=7.5)
ax.set_xticks(list(x)); ax.set_xticklabels(labels, fontsize=8)
ax.set_ylabel("position RMS (m)"); ax.set_title("Orbit determination: 6 h arc residual reduction", fontsize=11)
ax.grid(alpha=0.25, axis="y"); ax.legend(fontsize=8)
fig.tight_layout(); fig.savefig(os.path.join(OUT,"plot_fit.png"), dpi=200); plt.close(fig)

print("plots rendered")
