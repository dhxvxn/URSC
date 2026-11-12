"""Export stored campaign states faithfully; does not propagate or interpolate."""
from pathlib import Path
from decimal import Decimal
from datetime import datetime, timezone
import csv, json, hashlib, zipfile, math

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
GROUPS = {
 'original-four-tier': ROOT/'docs/reports/four-tier-comparison/results',
 'improved-broadcast': ROOT/'docs/reports/drag-maneuver-improvements/nominal/results',
 'retrospective-old-maneuvers': ROOT/'docs/reports/drag-maneuver-improvements/old-burns/results',
 'retrospective-corrected-maneuvers': ROOT/'docs/reports/drag-maneuver-improvements/new-burns/results',
}
created = datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%S')
fields = ['x_itrf_m','y_itrf_m','z_itrf_m','vx_itrf_ms','vy_itrf_ms','vz_itrf_ms']
coverage=[]; manifest=[]; totals={}; count=0
for group, root in GROUPS.items():
 dest=OUT/group; dest.mkdir(exist_ok=True)
 segments=[]; issued=0
 for case in sorted(root.iterdir()):
  if not case.is_dir(): continue
  completed=case/'completed.json'
  if not completed.exists(): continue
  meta=json.loads(completed.read_text()); src=case/'forecast.csv'
  status=meta.get('status', 'ISSUED' if meta.get('returncode')==0 else 'OD_NOT_CONVERGED')
  entry={'group':group,'case':case.name,'status':status,'oem_file':'','start_utc':'','stop_utc':'','states':0}
  if not src.exists(): coverage.append(entry); continue
  assert meta.get('returncode')==0, case
  rows=list(csv.DictReader(src.open())); assert len(rows)==8, case
  epochs=[r['epoch_utc'].removesuffix(' UTC') for r in rows]
  dates=[datetime.fromisoformat(e) for e in epochs]
  assert all((b-a).total_seconds()==86400 for a,b in zip(dates,dates[1:])),case
  tier=case.name[11:]; retro=group.startswith('retrospective')
  comments=[f'Campaign: {group}; case: {case.name}',f'GPS product tier: {tier}',f'UTC cutoff: {epochs[0]}',
    'Export of archived results; no new propagation, resampling or interpolation.',
    'Daily knots only: 86400 s spacing; NOT a dense orbit track.',
    'Do not interpolate these daily samples for tracking or operations.',
    'No covariance exported: archived CSV contains no covariance.',
    'Future measured weather/EOP not used; original past-only assumptions retained.',
    'Product publication vintages were not verified; not certified as-issued forecasts.']
  if tier in ('final','rapid'): comments+=['Retrospective GNSS precise products may incorporate post-cutoff information.']
  if retro: comments+=['RETROSPECTIVE MANEUVER REPLAY: uses future reference-derived unconfirmed burns.']
  lines=['COMMENT '+s for s in comments]+['META_START','OBJECT_NAME = SENTINEL-3A','OBJECT_ID = 2016-011A','CENTER_NAME = EARTH','REF_FRAME = ITRF','TIME_SYSTEM = UTC',f'START_TIME = {epochs[0]}',f'STOP_TIME = {epochs[-1]}','META_STOP','COMMENT Position km; velocity km/s. No interpolation method asserted.']
  for e,r in zip(epochs,rows):
   values=[Decimal(r[f])/1000 for f in fields]
   assert all(math.isfinite(float(v)) for v in values)
   lines.append(e+' '+' '.join(format(v,'.12f') for v in values))
  segment='\n'.join(lines)+'\n'; header=f'CCSDS_OEM_VERS = 2.0\nCREATION_DATE = {created}\nORIGINATOR = URSC\n\n'
  name=f'S3A_{case.name}_{group}.oem'; target=dest/name; target.write_text(header+segment,encoding='ascii')
  # Independent text-value comparison: on-wire kilometres restore exact source SI decimals.
  state_lines=[s for s in target.read_text().splitlines() if s[:4].isdigit()]
  assert len(state_lines)==8
  for r,line in zip(rows,state_lines):
   tokens=line.split(); assert tokens[0]==r['epoch_utc'].removesuffix(' UTC')
   assert all(Decimal(t)*1000==Decimal(r[f]) for t,f in zip(tokens[1:],fields))
  entry.update(oem_file=str(target.relative_to(OUT)), start_utc=epochs[0],stop_utc=epochs[-1],states=len(rows))
  coverage.append(entry); issued+=1; count+=1; segments.append(segment)
  manifest.append({'oem':str(target.relative_to(OUT)),'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'source_csv':str(src.relative_to(ROOT)),'source_sha256':hashlib.sha256(src.read_bytes()).hexdigest()})
 # One multi-segment file per group. Forecast windows deliberately overlap; no stitching.
 combined=OUT/(group+'-all-issued.oem')
 combined.write_text(header+'COMMENT Independent overlapping forecast segments; do not stitch as a single trajectory.\n\n'+'\n'.join(segments),encoding='ascii')
 totals[group]={'attempted':sum(e['group']==group for e in coverage),'issued':issued,'not_issued':sum(e['group']==group and not e['oem_file'] for e in coverage)}
with (OUT/'coverage.csv').open('w',newline='') as f:
 w=csv.DictWriter(f,fieldnames=list(coverage[0]));w.writeheader();w.writerows(coverage)
(OUT/'manifest.json').write_text(json.dumps({'created_utc':created,'groups':totals,'individual_oem_files':count,'source_values_verified':True,'files':manifest},indent=2)+'\n')
print(json.dumps(totals,indent=2))
