"""Summarize scored daily endpoint errors; missing forecasts are never treated as zero."""
from pathlib import Path
import csv,json,math,statistics
R=Path(__file__).resolve().parent
rows={r['date']:r for r in csv.DictReader((R/'daily-errors.csv').open())}
inputs=json.loads((R/'forecast-manifest.json').read_text())['inputs'];output=[]
for item in inputs:
 day=item['date'];row=rows.get(day,{'date':day,'day1_error_m':'','day7_error_m':'','cutoff_position_error_m':'','day1_status':'NO_FORECAST','day7_status':'NO_FORECAST'})
 log=R/'results'/day/'run.log';message=log.read_text() if log.exists() else ''
 if (R/'results'/day/'forecast.csv').exists():status='ISSUED'
 elif 'Batch initialization did not converge' in message:status='OD_NOT_CONVERGED'
 elif 'Insufficient solved SPP epochs' in message:status='INSUFFICIENT_SPP'
 elif 'Exception in thread' in message:status='EXECUTION_FAILED'
 else:status='PENDING'
 row['forecast_status']=status;output.append(row)
summary=[]
for key,horizon,limit,inclusive in [('day1_error_m',1,10,True),('day7_error_m',7,1000,False)]:
 values=[float(x[key]) for x in output if x.get(key)]
 values.sort()
 if values:
  rank=.95*(len(values)-1);lo=int(rank);f=rank-lo;p95=values[lo]*(1-f)+values[min(lo+1,len(values)-1)]*f
  summary.append({'horizon_days':horizon,'scored':len(values),'median_m':statistics.median(values),'rms_m':math.sqrt(sum(x*x for x in values)/len(values)),'p95_m':p95,'worst_m':max(values),'target_m':limit,'target_passes':sum(x<=limit if inclusive else x<limit for x in values)})
(R/'summary.json').write_text(json.dumps({'daily_cases':len(output),'status_counts':{s:sum(x['forecast_status']==s for x in output) for s in sorted({x['forecast_status'] for x in output})},'horizons':summary},indent=2)+'\n')
def show(v):return f'{float(v):,.2f}' if v else '—'
s='| Cutoff date (2024) | Cutoff position error (m) | +1 day error (m) | +7 day error (m) | Status |\n|---|---:|---:|---:|---|\n'
for row in output:
 status=row['forecast_status']
 if row.get('day7_status')=='NO_REFERENCE':status+='; no +7d reference'
 s+=f"| {row['date']} | {show(row.get('cutoff_position_error_m'))} | {show(row.get('day1_error_m'))} | {show(row.get('day7_error_m'))} | {status} |\n"
(R/'daily-table.md').write_text(s)
(R/'daily-cases.json').write_text(json.dumps(output,indent=2)+'\n')
print(json.dumps(json.loads((R/'summary.json').read_text()),indent=2))
