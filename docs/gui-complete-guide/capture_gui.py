"""Capture a running local GUI documentation workspace (no orbit jobs are started).
Usage: python capture_gui.py --url 'http://127.0.0.1:PORT/#t=TOKEN'
Requires Python Playwright and Chromium; do not publish session tokens.
"""
from pathlib import Path
import argparse,json,time
from playwright.sync_api import sync_playwright
P=Path(__file__).resolve().parent
args=argparse.ArgumentParser();args.add_argument('--url',required=True);args.add_argument('--chromium',default='/usr/bin/chromium');a=args.parse_args()
params=json.loads((P.parents[1]/'src/main/resources/gui/params.json').read_text())
evidence={'screenshots':[],'seenParameterKeys':['workflow'],'browserErrors':[]}
with sync_playwright() as pw:
 browser=pw.chromium.launch(executable_path=a.chromium,headless=True,args=['--no-sandbox','--enable-webgl','--use-gl=angle','--use-angle=swiftshader'])
 page=browser.new_page(viewport={'width':1500,'height':1080},device_scale_factor=1)
 page.on('pageerror',lambda e:evidence['browserErrors'].append(str(e)))
 page.goto(a.url);page.wait_for_selector('#presentation-counts');page.wait_for_timeout(700)
 def tab(name):
  page.locator('#tabs button[data-tab="'+name+'"]').click();page.wait_for_timeout(250)
 def record(name,caption,locator=None,full=False):
  path=P/'screenshots'/f'{name}.png';page.wait_for_timeout(250)
  if locator is not None: locator.screenshot(path=str(path))
  else:page.screenshot(path=str(path),full_page=full)
  controls=page.locator('button,select,input,summary,a').evaluate_all('es=>es.filter(e=>e.getClientRects().length).map(e=>({tag:e.tagName,type:e.type||"",label:(e.getAttribute("aria-label")||e.getAttribute("title")||e.textContent||e.getAttribute("placeholder")||"").trim(),options:e.tagName==="SELECT"?Array.from(e.options).map(o=>({value:o.value,label:o.text})):undefined}))')
  labels=page.locator('#tab-run .field > label').evaluate_all('es=>es.filter(e=>e.getClientRects().length).map(e=>e.childNodes[0].textContent.trim())')
  for p in params['params']:
   if p['label'] in labels and p['key'] not in evidence['seenParameterKeys']:evidence['seenParameterKeys'].append(p['key'])
  evidence['screenshots'].append({'file':f'screenshots/{name}.png','caption':caption,'controls':controls})
 def load(cfg):
  page.evaluate('async c => { const m=await import("./js/run.js");m.loadConfig(c); }',cfg);page.wait_for_timeout(450)
 def group(name):return page.locator('#tab-run details.group').filter(has=page.locator('summary').filter(has_text=name)).first
 def show_group(name):
  page.locator('#tab-run details.group').evaluate_all('(es)=>es.forEach(e=>e.open=false)')
  g=group(name);g.evaluate('(e)=>e.open=true');g.scroll_into_view_if_needed();page.wait_for_timeout(150);return g
 # Archive with actual saved campaign data.
 record('01-saved-presentation','Saved presentation: real archived two-month results; no new computation.')
 page.locator('#tab-presentation details').evaluate('(e)=>e.open=true')
 record('02-saved-case-details','Saved case metadata and eight daily benchmark state samples.',page.locator('#presentation-details'))
 tab('data');record('03-data','Data catalog in the isolated documentation workspace. SIM_DEMO files are simulated.')
 page.get_by_role('button',name='+ Add files / folders',exact=True).click();record('04-file-picker-multi','Shared file/folder browser: multi-selection mode.',page.get_by_role('dialog'));page.get_by_role('dialog').get_by_role('button',name='Cancel',exact=True).click()
 page.get_by_role('button',name='👁 Watch folder',exact=True).click();record('05-file-picker-folder','Folder-selection mode also provides Use this folder.',page.get_by_role('dialog'));page.get_by_role('dialog').get_by_role('button',name='Cancel',exact=True).click()
 tab('run')
 default=page.evaluate('async()=>{const {api}=await import("./js/api.js");return api("config/default");}')
 (P/'startup-defaults.json').write_text(json.dumps(default,indent=2)+'\n')
 record('06-run-toolbar','Run toolbar, workflows, presets and initial groups. Missing files in a fresh setup are validation blockers, not new computations.')
 for i,name in enumerate(params['groups']):
  if name=='Initial state':continue
  g=show_group(name);record(f'07-{i:02d}-'+name.lower().replace(' ','-').replace('&','and'),f'Run → {name}: all currently visible controls.',g)
 # Density-model info and reset behavior.
 g=show_group('Perturbations');f=g.locator('.field').filter(has=page.locator('label').filter(has_text='Density model')).first
 f.locator('button.info').click();record('08-density-info','Density-model information popover: explanation, guidance, key and default.');page.locator('h2').filter(has_text='Run setup').click()
 box=json.loads(json.dumps(default));box['spacecraft']['shape']='BOX_WING';load(box)
 g=show_group('Spacecraft');record('09-box-wing','Box-wing conditional controls: attitude selector, face areas, array area and cant angle.',g)
 empirical=json.loads(json.dumps(default));empirical['forces']['empirical']['enabled']=True;load(empirical)
 g=show_group('Perturbations');record('10-empirical','Empirical acceleration enabled: radial, transverse and normal fields become visible.',g)
 load(default);page.get_by_role('button',name='Propagate from a state',exact=True).click()
 g=show_group('Initial state');record('11-propagate-typed','Propagate workflow with typed initial state: epoch, frame and six SI components.',g)
 source=g.locator('select').first;source.select_option('OEM_FILE');g=show_group('Initial state');record('12-propagate-oem','State source OEM file: the typed vector fields are replaced by a file input.',g)
 g.locator('button').filter(has_text='Browse').click();record('13-file-picker-single','Single-file picker used for OEM, POEORB and optional file inputs.',page.get_by_role('dialog'));page.get_by_role('dialog').get_by_role('button',name='Cancel',exact=True).click()
 g=show_group('Initial state');g.locator('select').first.select_option('POEORB_FILE');g=show_group('Initial state');record('14-propagate-poeorb','State source POEORB file; blank epoch selects the first state, otherwise selection uses the requested epoch.',g)
 load(default);page.get_by_role('button',name='Sweep…',exact=True).click();record('15-sweep-enum','Sweep dialog for an enum: parameter selector and choice checkboxes.',page.get_by_role('dialog'))
 dialog=page.get_by_role('dialog');dialog.locator('select').select_option('spacecraft.cd');record('16-sweep-numeric','Sweep dialog for a numeric setting: comma-separated values.',dialog)
 dialog.locator('select').select_option('forces.drag.enabled');record('17-sweep-boolean','Sweep dialog for a boolean: on/off checkboxes.',dialog);dialog.get_by_role('button',name='Cancel',exact=True).click()
 tab('results');record('18-results-empty','Results before selecting a local saved run. Bundled archive cases are separate from local History.')
 runs=page.evaluate('async()=>{const {api}=await import("./js/api.js");return (await api("runs")).runs;}')
 ids=[r['id'] for r in runs if 'SIMULATED controls demo' in r.get('runName','')]
 assert len(ids)>=2,'Prepare the isolated simulated documentation runs first'
 cov=next(r['id'] for r in runs if 'SIMULATED controls demo FORECAST'==r.get('runName'))
 page.evaluate('async id=>{const m=await import("./js/results.js");await m.openRun(id);}',cov);page.wait_for_timeout(800)
 record('19-results-loaded','Results for a short SIMULATED documentation run, not measured Sentinel-3A campaign accuracy.',full=True)
 viz=page.locator('#tab-results .viz').locator('..');record('20-results-visualization','Live visualization controls, map layers, playback and covariance ellipsoid using simulated data.',viz)
 page.get_by_title('Spacecraft model drawn on the globe (enlarged so it is visible)').select_option('sentinel3');page.get_by_role('button',name='🛰 Follow satellite',exact=True).click();record('21-results-follow','Sentinel-3 drawing explicitly selected for a SIMULATED trajectory: changes drawing only, not physics.',viz)
 page.get_by_role('button',name='🛰 Follow satellite',exact=True).click();page.get_by_title('Show the globe over the whole window (Esc to leave)').click();record('22-results-full-view','Full-view globe layout; Escape restores the normal page.');page.keyboard.press('Escape')
 station=page.locator('#tab-results .card').filter(has=page.get_by_role('heading',name='Ground-station passes',exact=True));station.locator('details').evaluate('(e)=>e.open=true');record('23-stations-passes','Station editor and pass table for a SIMULATED trajectory.',station)
 plots=page.locator('#tab-results .card').filter(has=page.get_by_role('heading',name='Plots',exact=True));record('24-results-plots','Conditional result plots: altitude, formal covariance, reference error and positioning diagnostics for the simulated case.',plots)
 detail=page.locator('#tab-results details').filter(has=page.locator('summary').filter(has_text='GNSS positioning per epoch')).first;detail.evaluate('(e)=>e.open=true');record('25-spp-per-epoch','GNSS positioning detail table expanded; screenshot is a short visible excerpt.',detail)
 tab('window');page.wait_for_timeout(450);record('26-window','Window controls illustrated by a clearly labelled one-date documentation record referring to the simulated run; not a rerun of the two-month campaign.',full=True)
 tab('history');record('27-history','Local history holding two short SIMULATED documentation runs; bundled two-month archive remains separate.')
 page.locator('#tab-history input.cmp').evaluate_all('(es)=>es.slice(0,2).forEach(e=>e.checked=true)');page.get_by_role('button',name='Compare selected',exact=True).click();page.wait_for_timeout(600);record('28-comparison','Comparison of two SIMULATED saved runs. Run-to-run differences are not real-world accuracy.',full=True)
 tab('help');record('29-help','Help tab with local documentation navigation and provider status.')
 page.locator('#tab-help input[type=search]').fill('What is the forecast cutoff?');page.locator('#tab-help').get_by_role('button',name='Search',exact=True).click();page.wait_for_timeout(500);record('30-help-search','Offline help search with passages and provider attribution.',full=True)
 for title in ['Input data','GPS product priority','Perturbations & models','Outputs (OEM, covariance)','Offline operation & AI','Troubleshooting']:
  page.locator('#tab-help .helpnav').get_by_role('button',name=title,exact=True).click();page.wait_for_timeout(100)
  # Inventory each page even though controls are the same.
  record('31-help-'+title.lower().replace(' ','-').replace('/','-').replace('&','and').replace(',','').replace('(','').replace(')',''),'Help guide: '+title,page.locator('#tab-help .helpgrid'))
 guide=browser.new_page(viewport={'width':1500,'height':1080});guide.goto(a.url.split('/#')[0]+'/guide/');guide.wait_for_timeout(250)
 guide.screenshot(path=str(P/'screenshots/32-interactive-guide.png'),full_page=False)
 evidence['screenshots'].append({'file':'screenshots/32-interactive-guide.png','caption':'Separate interactive feature guide: walkthrough steps and navigation. Historical explanatory text is not a replacement for current campaign provenance.','controls':guide.locator('button,input,a').evaluate_all('es=>es.map(e=>({tag:e.tagName,label:(e.textContent||e.placeholder||"").trim()}))')})
 guide.locator('#q').fill('OEM');guide.locator('#q').scroll_into_view_if_needed();guide.screenshot(path=str(P/'screenshots/33-guide-filter.png'))
 evidence['screenshots'].append({'file':'screenshots/33-guide-filter.png','caption':'Guide feature filter; results depend on all typed terms.','controls':[]})
 guide.locator('figure[data-zoom]').first.click();guide.screenshot(path=str(P/'screenshots/34-guide-zoom.png'))
 evidence['screenshots'].append({'file':'screenshots/34-guide-zoom.png','caption':'Guide screenshot zoom modal; Close, Escape or backdrop dismiss it.','controls':[]})
 browser.close()
keys={p['key'] for p in params['params']};evidence['missingParameterKeys']=sorted(keys-set(evidence['seenParameterKeys']))
evidence['sourceParameters']=len(keys)
(P/'capture-evidence.json').write_text(json.dumps(evidence,indent=2,ensure_ascii=False)+'\n')
print(json.dumps({'screenshots':len(evidence['screenshots']),'parameters_seen':len(evidence['seenParameterKeys']),'missing':evidence['missingParameterKeys'],'browser_errors':evidence['browserErrors']}))
