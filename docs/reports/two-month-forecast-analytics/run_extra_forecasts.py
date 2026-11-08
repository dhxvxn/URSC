"""Use the fifth CPU for one controlled retry and the final dates; no truth access."""
from pathlib import Path
import json,subprocess,time,shutil
ROOT=Path(__file__).resolve().parents[3];REPORT=Path(__file__).resolve().parent
runtime=ROOT/'target/analytics-runtime'
if not runtime.exists():shutil.copytree(ROOT/'target/classes',runtime)
def active(day):
 for p in Path('/proc').iterdir():
  if not p.name.isdigit():continue
  try:a=(p/'cmdline').read_bytes().split(b'\0')
  except OSError:continue
  if b'org.ursc.trajectory.app.command.DailyForecast' in a and day.encode() in a:return True
 return False
while active('2024-09-03'):time.sleep(2)
items=json.loads((REPORT/'forecast-manifest.json').read_text())['inputs'];bydate={x['date']:x for x in items}
plan=[bydate['2024-09-15']]+list(reversed(items[-10:]));statuses=[]
for item in plan:
 day=item['date'];out=REPORT/'results'/day
 if (out/'forecast.csv').exists() or active(day):continue
 out.mkdir(parents=True,exist_ok=True)
 if (out/'run.log').exists():shutil.copy2(out/'run.log',out/'run-before-retry.log')
 command=['/workspace/.tools/java/bin/java','-Xmx384m','-cp','target/analytics-runtime:target/daily-forecast','org.ursc.trajectory.app.command.DailyForecast',item['observation'],item['navigation'],str(out),day]
 start=time.monotonic()
 with (out/'run.log').open('w') as log:
  result=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,timeout=1800)
 r={'date':day,'status':'FORECAST_SAVED' if result.returncode==0 else 'INITIALIZATION_OR_PROPAGATION_FAILED','seconds':round(time.monotonic()-start,3)}
 statuses.append(r);(REPORT/'extra-forecast-status.json').write_text(json.dumps(statuses,indent=2)+'\n');print(json.dumps(r),flush=True)
print('Extra forecast worker finished.',flush=True)
