import csv, glob, math, sys, statistics as st, os, shutil
C, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
def load(prefix):
    rows = {}
    for f in sorted(glob.glob(f"{C}/{prefix}-*.csv")):
        for r in csv.DictReader(open(f)):
            rows[r['date']] = r
    return dict(sorted(rows.items()))
def num(x):
    try:
        v = float(x); return v if math.isfinite(v) else None
    except: return None
names = [('before', 'spp', 'Before: SPP position fit (previous engine)'),
         ('after', 'cp', 'After: carrier-phase OD (in-house engine)'),
         ('orekit', 'ok', 'Final: carrier-phase OD on the Orekit engine')]
runs = {n: load(p) for n, p, _ in names}
H = [('e0h','At cut-off'),('e1h','+1 h'),('e6h','+6 h'),('e1d','+1 day'),('e3d','+3 days'),('e7d','+7 days')]
def pct(v, p):
    v = sorted(v); k = (len(v)-1)*p; i = int(k); return v[i] if i+1 >= len(v) else v[i]+(v[i+1]-v[i])*(k-i)
def fmt(x):
    if x is None: return '—'
    return f"{x:,.2f}" if x < 100 else f"{x:,.0f}"
md = []
md.append("| Run | Days | Successful | Median fit RMS | Median run time |")
md.append("|---|---:|---:|---|---:|")
for n, p, label in names:
    rows = runs[n]; ok = [r for r in rows.values() if r['status']=='SUCCESS']
    rms = [num(r['postFitRms']) for r in ok if num(r['postFitRms'])]
    unit = 'positions' if n == 'before' else 'carrier phase'
    md.append(f"| {label} | {len(rows)} | {len(ok)} | {st.median(rms):.3f} m ({unit}) | {st.median([num(r['seconds']) for r in ok]):.0f} s |" if ok else f"| {label} | {len(rows)} | 0 | — | — |")
md.append("")
md.append("**3-D position error against POEORB (m), median over the scored days** (95th percentile in brackets):")
md.append("")
md.append("| Horizon | " + " | ".join(l.split(':')[0] for _, _, l in names) + " | Change (final vs before) |")
md.append("|---|" + "---:|"*len(names) + "---:|")
for k, lab in H:
    cells = []; meds = {}
    for n, _, _ in names:
        v = [num(r[k]) for r in runs[n].values() if r['status']=='SUCCESS' and num(r[k]) is not None]
        if v:
            meds[n] = st.median(v); cells.append(f"{fmt(st.median(v))} ({fmt(pct(v,.95))})")
        else: cells.append('—')
    ch = f"{100*(meds['orekit']-meds['before'])/meds['before']:+.0f} %" if 'orekit' in meds and 'before' in meds else '—'
    md.append(f"| {lab} | " + " | ".join(cells) + f" | {ch} |")
summary = "\n".join(md)
# per-day table
days = sorted(set().union(*[set(r) for r in runs.values()]))
t = ["| Cut-off (2024) | Before: cut-off | After: cut-off | Orekit: cut-off | Before: +1 day | After: +1 day | Orekit: +1 day | Before: +7 days | After: +7 days | Orekit: +7 days | Orekit phase RMS |",
     "|---|" + "---:|"*10]
for d in days:
    c = []
    for k in ('e0h','e1d','e7d'):
        for n, _, _ in names:
            r = runs[n].get(d)
            c.append(fmt(num(r[k])) if r and r['status']=='SUCCESS' else ('—' if not r else r['status']))
    r = runs['orekit'].get(d)
    c.append(f"{100*num(r['phaseRms']):.1f} cm" if r and num(r['phaseRms']) else '—')
    t.append(f"| {d[5:]} | " + " | ".join(c) + " |")
daily = "\n".join(t)
open(f"{OUT}/summary.md","w").write(summary+"\n")
open(f"{OUT}/daily-table.md","w").write(daily+"\n")
for n, p, _ in names:
    with open(f"{OUT}/{n}.csv","w") as f:
        rows = list(runs[n].values())
        if rows:
            w = csv.DictWriter(f, fieldnames=list(rows[0].keys())); w.writeheader(); [w.writerow(r) for r in rows]
print(summary)
