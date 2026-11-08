"""Render the accuracy report from scored outputs, without altering any forecast."""
from pathlib import Path
import json
R=Path(__file__).resolve().parent
s=json.loads((R/'summary.json').read_text())
a=json.loads((R/'input-verification.json').read_text())
text='# Two-month forecast error results\n\n'
text+='| Horizon | Scored cases | Median error (m) | RMS error (m) | 95th percentile (m) | Worst error (m) | Target | Cases meeting target |\n|---|---:|---:|---:|---:|---:|---|---:|\n'
for h in s['horizons']:
 target='≤10 m' if h['horizon_days']==1 else '<1,000 m'
 text+=f"| {h['horizon_days']} day(s) | {h['scored']} | {h['median_m']:,.2f} | {h['rms_m']:,.2f} | {h['p95_m']:,.2f} | {h['worst_m']:,.2f} | {target} | {h['target_passes']}/{h['scored']} |\n"
text+='\nWindow: 3 September–2 November 2024; 61 daily forecast cutoffs at 05:59:42 UTC. Errors are exact-endpoint 3D position differences against POEORB, in metres.\n\n'
text+='| Processing status | Days |\n|---|---:|\n'
for k,v in s['status_counts'].items():text+=f'| {k} | {v} |\n'
text+='\n'+(R/'daily-table.md').read_text()
text+='\nA dash is an unavailable score, not a zero error. Nonconverged OD solutions do not issue forecasts. Seven-day scores for cutoffs 29 October–2 November require POEORB for 5–9 November.\n'
text+='\nGNSS observations and broadcast navigation initialize a six-hour covariance-weighted batch OD arc. Future weather uses solar persistence and nominal Ap=5; Earth orientation persists cutoff-eligible past values. No future observed weather, measured attitude or POEORB is supplied to propagation.\n'
text+='\nThis is a historical measurement-cutoff replay: exact as-issued publication vintages are unverified. The benchmark evaluates position accuracy, not propagated covariance. See [protocol and architecture routing](README.md) for configuration and limits.\n'
text+=f"\nInput verification: {a['observation_files']} GNSSRD archives, {a['navigation_files']} broadcast files, {a['reference_files']} POEORB references; no missing paths or hash mismatches. Numerical validation: {a['tests']['tests']} tests, {a['tests']['failures']} failures, {a['tests']['errors']} errors.\n"
text+='\nMachine-readable outputs: [daily errors](daily-errors.csv), [endpoint errors including RTN](endpoint-errors.csv), [summary](summary.json), [frozen inputs and settings](forecast-manifest.json).\n'
(R/'RESULTS.md').write_text(text)
print('Rendered',R/'RESULTS.md')
