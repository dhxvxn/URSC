"""Check completed forecast artifacts and scoring completeness; never change them."""
from pathlib import Path
import csv,json,math
R=Path(__file__).resolve().parent
manifest=json.loads((R/'forecast-manifest.json').read_text())
summary=json.loads((R/'summary.json').read_text())
cases=json.loads((R/'daily-cases.json').read_text())
problems=[];issued=0
for case in cases:
 day=case['date'];p=R/'results'/day
 if case['forecast_status']=='PENDING':problems.append(day+': unfinished')
 if case['forecast_status']!='ISSUED':continue
 issued+=1
 rows=list(csv.DictReader((p/'forecast.csv').open()))
 if [float(r['elapsed_days']) for r in rows]!=list(range(8)):problems.append(day+': missing daily samples')
 for row in rows:
  if not all(math.isfinite(float(v)) for k,v in row.items() if k!='epoch_utc'):problems.append(day+': nonfinite state')
 props=dict(line.split('=',1) for line in (p/'fit.properties').read_text().splitlines())
 if props['converged']!='true':problems.append(day+': issued despite OD failure')
 if not case.get('day1_error_m'):problems.append(day+': missing day1 score')
 if not case.get('day7_error_m') and not ('2024-10-29'<=day<='2024-11-02' and case.get('day7_status')=='NO_REFERENCE'):
  problems.append(day+': unexpected missing day7 score')
if len(cases)!=61:problems.append('Expected 61 cases')
if issued!=summary['status_counts'].get('ISSUED',0):problems.append('Issued count mismatch')
report={'cases':len(cases),'issued':issued,'problems':problems,'passed':not problems}
(R/'output-verification.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
if problems:raise SystemExit(1)
