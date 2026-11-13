"""Tables for the improvement steps: 7-day ablation and the 61-day campaign (markdown)."""
import csv, glob, math, statistics as st, sys, os, shutil

C = sys.argv[1]
OUT = sys.argv[2]
os.makedirs(OUT, exist_ok=True)
TEST = ['2024-09-03', '2024-09-11', '2024-09-19', '2024-09-27', '2024-10-06', '2024-10-19', '2024-10-27']


def load(pat):
    r = {}
    for f in sorted(glob.glob(pat)):
        for x in csv.DictReader(open(f)):
            r[x['date']] = x
    return r


def num(x):
    try:
        v = float(x)
        return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def fmt(x, cm=False):
    if x is None:
        return '—'
    if cm:
        return f"{100 * x:.1f} cm"
    return f"{x:,.2f}" if x < 100 else f"{x:,.0f}"


def pct(v, p):
    v = sorted(v)
    k = (len(v) - 1) * p
    i = int(k)
    return v[i] if i + 1 >= len(v) else v[i] + (v[i + 1] - v[i]) * (k - i)


H = [('e0h', 'At cut-off'), ('e1h', '+1 h'), ('e6h', '+6 h'), ('e1d', '+1 day'), ('e3d', '+3 days'), ('e7d', '+7 days')]
before = load(C + '/spp-*.csv')
orekit = load(C + '/ok-*.csv')
final = load(C + '/fin-*.csv')
s1 = load(C + '/var-cd-arc12.csv')
s1_6 = load(C + '/var-cd-arc6.csv')
s3off = load(C + '/var-s3-cd-arc12-nopco-nowu.csv')
jb = load(C + '/var-s3-cd-arc12-jb.csv')
box = load(C + '/var-s3-cd-arc12-box.csv')


def med(R, k, days):
    v = [num(R[d][k]) for d in days if d in R and R[d]['status'] == 'SUCCESS' and num(R[d][k]) is not None]
    return (st.median(v), len(v)) if v else (None, 0)


# ---- ablation on the test days (each column on the days it has)
cols = [('Orekit carrier phase (before steps)', orekit), ('Step 1: Cd, 6 h arc', s1_6), ('Step 1: Cd, 12 h arc', s1),
        ('Steps 1+4 (no GPS antennas)', s3off), ('Steps 1+3+4 (all)', final), ('Step 2: + JB2008', jb),
        ('Step 2: + box-wing, PROQUA', box)]
abl = ["| Median over test days | " + " | ".join(c for c, _ in cols) + " |", "|---|" + "---:|" * len(cols)]
for k, lab in [('phaseRms', 'Phase residual RMS')] + H:
    row = []
    for _, R in cols:
        days = [d for d in TEST if d in R]
        m, n = med(R, k, days)
        row.append(fmt(m, cm=(k == 'phaseRms')) + (f" ({n} d)" if k == 'e0h' else ''))
    abl.append(f"| {lab} | " + " | ".join(row) + " |")
open(OUT + '/ablation.md', 'w').write("\n".join(abl) + "\n")

# ---- 61-day campaign: before / first rebuild / final
camp = ["| Horizon | Before (SPP fit) | Carrier phase on Orekit | **Final (steps 1–5)** | Change vs before | Change vs Orekit rebuild |",
        "|---|---:|---:|---:|---:|---:|"]
for k, lab in H:
    vals = []
    for R in (before, orekit, final):
        v = [num(r[k]) for r in R.values() if r['status'] == 'SUCCESS' and num(r[k]) is not None]
        vals.append((st.median(v), pct(v, .95)) if v else (None, None))
    def ch(a, b):
        return f"{100 * (b - a) / a:+.0f} %" if a and b else '—'
    camp.append(f"| {lab} | {fmt(vals[0][0])} ({fmt(vals[0][1])}) | {fmt(vals[1][0])} ({fmt(vals[1][1])}) | "
                f"**{fmt(vals[2][0])}** ({fmt(vals[2][1])}) | {ch(vals[0][0], vals[2][0])} | {ch(vals[1][0], vals[2][0])} |")
ok = [r for r in final.values() if r['status'] == 'SUCCESS']
camp.append("")
camp.append(f"Final run: {len(final)} cut-offs, {len(ok)} successful; median phase residual "
            f"{100 * st.median([num(r['phaseRms']) for r in ok if num(r['phaseRms'])]):.1f} cm; median run time "
            f"{st.median([num(r['seconds']) for r in ok]):.0f} s per cut-off (four in parallel).")
open(OUT + '/campaign.md', 'w').write("\n".join(camp) + "\n")

# ---- per-day table
days = sorted(set(before) | set(orekit) | set(final))
t = ["| Cut-off (2024) | Before: cut-off | Final: cut-off | Before: +1 day | Orekit: +1 day | Final: +1 day | Before: +7 days | Orekit: +7 days | Final: +7 days |",
     "|---|" + "---:|" * 8]
for d in days:
    def g(R, k):
        r = R.get(d)
        return fmt(num(r[k])) if r and r['status'] == 'SUCCESS' else ('—' if not r else r['status'])
    t.append(f"| {d[5:]} | {g(before,'e0h')} | {g(final,'e0h')} | {g(before,'e1d')} | {g(orekit,'e1d')} | {g(final,'e1d')} | "
             f"{g(before,'e7d')} | {g(orekit,'e7d')} | {g(final,'e7d')} |")
open(OUT + '/daily-table.md', 'w').write("\n".join(t) + "\n")
with open(OUT + '/final.csv', 'w') as f:
    rows = list(final.values())
    if rows:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        [w.writerow(r) for r in rows]
for name, R in [('ablation-cd-arc6', s1_6), ('ablation-cd-arc12', s1), ('ablation-no-gps-antennas', s3off),
                ('ablation-jb2008', jb), ('ablation-box-wing', box)]:
    rows = list(R.values())
    if rows:
        with open(f"{OUT}/{name}.csv", 'w') as f:
            w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
            w.writeheader()
            [w.writerow(r) for r in rows]
print(open(OUT + '/ablation.md').read())
print(open(OUT + '/campaign.md').read())
