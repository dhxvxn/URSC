"""Bundle the already-computed campaign; never runs an estimator/propagator.
Run from any directory. Source CSVs are needed only when refreshing the snapshot.
"""
from pathlib import Path
import csv, json, hashlib, shutil, argparse
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--archive-root',type=Path,default=ROOT)
ARCHIVE=parser.parse_args().archive_root
OUT=ROOT/'src/main/resources/gui/presentation'
EXPORT=ROOT/'docs/reports/two-month-oem-export'
GROUPS={
'original-four-tier':'docs/reports/four-tier-comparison',
'improved-broadcast':'docs/reports/drag-maneuver-improvements/nominal',
'retrospective-old-maneuvers':'docs/reports/drag-maneuver-improvements/old-burns',
'retrospective-corrected-maneuvers':'docs/reports/drag-maneuver-improvements/new-burns'}
def numeric(s):
 try:return float(s)
 except (ValueError,TypeError):return None
rows=[]
for group,rel in GROUPS.items():
 folder=ARCHIVE/rel
 scores={}
 for r in csv.DictReader((folder/'endpoint-errors.csv').open()):
  scores[(r['date'],r['elapsed_days'])]={'status':r['status'],'positionErrorM':numeric(r['position_error_m'])}
 for r in csv.DictReader((EXPORT/'coverage.csv').open()):
  if r['group']!=group:continue
  case=folder/'results'/r['case']
  data=dict(r);data['tier']=r['case'][11:];data['date']=r['case'][:10]
  data['scores']={str(day):scores.get((r['case'],str(day))) for day in (0,1,7)}
  data['samples']=[];data['inputs']={};data['fit']={};data['environment']={}
  for name,key in [('input.properties','inputs'),('fit.properties','fit'),('environment.properties','environment')]:
   f=case/name
   if f.exists():
    for line in f.read_text().splitlines():
     if '=' not in line or line.startswith('#'):continue
     k,v=line.split('=',1)
     if key=='inputs' and ('/' in v or '\\' in v):v=Path(v).name
     data[key][k]=v
  if r['oem_file']:
   src=case/'forecast.csv';content=src.read_bytes()
   # Snapshot must correspond exactly to the previously verified OEM source.
   manifest=json.loads((EXPORT/'manifest.json').read_text())
   recorded=next(x for x in manifest['files'] if x['oem']==r['oem_file'])
   assert hashlib.sha256(content).hexdigest()==recorded['source_sha256']
   data['sourceSha256']=recorded['source_sha256']
   data['samples']=list(csv.DictReader(src.open()))
   data['oem']=(EXPORT/r['oem_file']).read_text()
  rows.append(data)
snapshot={'version':1,'satellite':'Sentinel-3A','cutoffs':'2024-09-03 to 2024-11-02','sampleRange':'2024-09-03 to 2024-11-09','spacingSeconds':86400,'groups':list(GROUPS),'cases':rows}
OUT.mkdir(parents=True,exist_ok=True)
(OUT/'snapshot.json').write_text(json.dumps(snapshot,separators=(',',':'),allow_nan=False)+'\n')
for name in ['TWO_MONTH_OEM_PACKAGE.zip','OEM_OUTPUT_REPORT.pdf','coverage.csv']:
 shutil.copyfile(EXPORT/name,OUT/name)
print(f'Bundled {len(rows)} attempts / {sum(bool(r["samples"]) for r in rows)} outputs')
