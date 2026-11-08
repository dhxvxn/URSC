"""Freeze inputs/configuration and issue independent daily forecasts. No truth loading."""
from pathlib import Path
from datetime import date,timedelta
import re,json,hashlib,subprocess,concurrent.futures,time,os
ROOT=Path(__file__).resolve().parents[3]
REPORT=Path(__file__).resolve().parent
DATA=Path('/workspace/URSC-nav-publish/data')
def sha(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
obs={}
for p in (DATA/'gnss/observations/2024').glob('S3A*.tgz'):
 m=re.search(r'_V\d+T\d+_(\d{8})',p.name);obs[m[1]]=p
p=next(Path('/workspace/attachments/567fbf32-04a0-4db9-a9ad-506ec558ae57').glob('*.tgz'));obs['20240903']=p
nav={x['date_gps']:DATA/'gnss/navigation/2024'/x['file'] for x in json.loads((DATA/'gnss/navigation/2024/manifest.json').read_text())['files']}
start=date(2024,9,3);days=[start+timedelta(days=i) for i in range(61)]
inputs=[]
for day in days:
 inputs.append({'date':day.isoformat(),'observation':str(obs[day.strftime('%Y%m%d')]),'observation_sha256':sha(obs[day.strftime('%Y%m%d')]),'navigation':str(nav[day.isoformat()]),'navigation_sha256':sha(nav[day.isoformat()])})
manifest={'classification':'historical measurement-cutoff replay; product publication vintages unverified','initialization':'six-hour GNSS SPP and covariance-weighted batch OD, fixed Cd','cutoff':'06:00 GPS = 05:59:42 UTC on each day','fit_hours':6,'sample_step_s':300,'range_sigma_m':5,'forecast_horizons_days':[1,7],'cd':2.2,'cr':1.3,'mass_kg':1130,'drag_area_m2':8,'srp_area_m2':8,'gravity_degree_order':70,'atmosphere':'NRLMSISE-00','weather':'causal trailing solar average, persistent solar flux, nominal future Ap5','EOP':'latest completed-day row persisted','future_attitude':'fixed effective areas; no measured future attitude','numerics':'DP54 abs1e-10 rel1e-13 max30s','future_truth_used_for_forecasting':False,'uncertainty':'not evaluated in this endpoint benchmark','inputs':inputs,'source_sha256':{str(p.relative_to(ROOT)):sha(p) for p in (ROOT/'src/main/java').rglob('*.java')},'harness_sha256':sha(REPORT/'DailyForecast.java')}
for name in ['SpaceWeather-All-v1.2.txt','finals2000A.all']:
 p=ROOT/'docs/reports/perturbation-ladder-truestate-7day/inputs'/name;manifest[name+'_sha256']=sha(p)
(REPORT/'forecast-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
java='/workspace/.tools/java/bin/java'
def run(item):
 day=item['date'];out=REPORT/'results'/day;out.mkdir(parents=True,exist_ok=True)
 if (out/'forecast.csv').exists():return {'date':day,'status':'FORECAST_SAVED','cached':True}
 command=[java,'-Xmx384m','-cp','target/classes:target/daily-forecast','org.ursc.trajectory.app.command.DailyForecast',item['observation'],item['navigation'],str(out),day]
 begin=time.monotonic()
 with (out/'run.log').open('w') as log:
  try:
   completed=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,timeout=1800)
   status='FORECAST_SAVED' if completed.returncode==0 else 'INITIALIZATION_OR_PROPAGATION_FAILED'
  except subprocess.TimeoutExpired:status='COMPUTATION_TIMEOUT'
 return {'date':day,'status':status,'seconds':round(time.monotonic()-begin,3)}
# The initial pilot is already running separately; remaining cases use four workers.
items=[x for x in inputs if x['date']!='2024-09-03']
statuses=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
 for future in concurrent.futures.as_completed([pool.submit(run,item) for item in items]):
  r=future.result();statuses.append(r)
  (REPORT/'forecast-status.json').write_text(json.dumps(sorted(statuses,key=lambda x:x['date']),indent=2)+'\n')
  print(json.dumps(r),flush=True)
print('All queued forecasts finished. Reference orbits have not been read.',flush=True)
