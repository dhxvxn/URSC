"""Generate the exhaustive illustrated GUI manual from its audited control registry."""
from pathlib import Path
import json,re,hashlib,html,base64,zipfile,subprocess
P=Path(__file__).resolve().parent;ROOT=P.parents[1]
params=json.loads((ROOT/'src/main/resources/gui/params.json').read_text())
controls=json.loads((P/'controls.json').read_text())
default=json.loads((P/'startup-defaults.json').read_text())
evidence=json.loads((P/'capture-evidence.json').read_text())
def get(path):
 x=default
 for k in path.split('.'):x=x.get(k) if isinstance(x,dict) else None
 return x
option_notes={
'workflow':{'OD_FORECAST':'Fit pre-cutoff GNSS fixes, then forecast from the estimated cutoff state.','PROPAGATE':'Use an existing typed or file-based state; do not run GNSS orbit determination.'},
'products.mode':{'FORECAST':'Restrict inputs using the configured cutoff policy. Publication assumptions must still be disclosed.','RECONSTRUCTION':'Allow later retrospective products; do not call this an as-issued forecast.'},
'products.eligibility':{'NOMINAL_LATENCY':'Assume class-dependent publication delays; this is an estimate, not proof of actual issue time.','REQUIRE_VERIFIED':'Require recorded issue-time evidence. Products without acceptable evidence can be excluded.'},
'products.tier':{'AUTO':'Choose best eligible product per satellite/epoch: Final → Rapid → Ultra-Rapid → Broadcast.','FINAL':'Restrict to Final-class precise products; eligible coverage must exist.','RAPID':'Restrict to Rapid-class products.','ULTRA_RAPID':'Restrict to Ultra-Rapid products; observed/predicted status and cutoff rules still apply.','BROADCAST':'Restrict to broadcast navigation ephemerides.'},
'forecast.frame':{'EME2000':'Write inertial Earth mean equator/equinox of J2000 coordinates.','GCRF':'Write geocentric celestial inertial-frame coordinates.','ITRF':'Write rotating Earth-fixed terrestrial coordinates. Velocity must use the corresponding rotating-frame transform.'},
'initialState.frame':{'EME2000':'Interpret the typed vector in EME2000.','GCRF':'Interpret the typed vector in GCRF.','ITRF':'Interpret the typed vector in Earth-fixed ITRF; position and velocity must both belong to that frame.'},
'spacecraft.shape':{'CANNONBALL':'Use constant drag/SRP areas, Cd, Cr and mass; the separately entered face areas are not used.','BOX_WING':'Use attitude-dependent projected body/array geometry; exposes attitude, face areas, wing area and cant.'},
'spacecraft.attitude':{'NOMINAL_YAW_STEERING':'Use the modelled nominal orientation; no measured-attitude input required. This is an assumption, not recovered telemetry.','MEASURED_PROQUA':'Use eligible measured quaternion inputs where supplied/covered; inspect cutoff and fallback warnings.'},
'forces.dragModel':{'NRLMSISE00':'Empirical NRLMSISE-00 thermospheric density driven by solar/geomagnetic inputs.','JB2008':'JB2008 implementation with its required solar/geomagnetic drivers; verify driver coverage and future-input policy.','HARRIS_PRIESTER':'Simplified diurnal-density model; useful for sensitivity studies and quick checks.','EXPONENTIAL':'Simple altitude-density approximation; useful as a baseline, not a guarantee of precise prediction.'},
'forces.thirdBodyEphemeris':{'ANALYTIC':'Use bundled analytical Sun/Moon series without a DE440 file.','DE440':'Use a supplied compatible JPL DE440 file. More detailed ephemerides are not mathematically exact or an automatic accuracy improvement.'},
'weather.afterCutoff':{'PERSIST':'Use the cutoff-based persistence policy and configured forecast Ap; do not read future measured drivers.','USE_FILE':'Use later file values: retrospective scenario, not operational future-weather prediction.'},
'eop.afterCutoff':{'PERSIST':'Hold cutoff Earth-orientation values according to the configured policy.','USE_FILE':'Read later EOP file values; label the experiment retrospective.'},
'initialState.source':{'TYPED':'Enter epoch, frame, XYZ in metres and velocity in m/s.','OEM_FILE':'Choose an OEM and select the requested/first starting state; this is propagation from supplied ephemeris.','POEORB_FILE':'Choose a POEORB and select the starting state; this deliberately uses a precise seed and is not GNSS-only OD.'},
}
corrections={
'inputs.gpsProducts':('GPS satellite orbit and clock information used to interpret the spacecraft observations. Supply compatible broadcast navigation or precise orbit/clock products.','Choose by coverage, provider/solution compatibility and issue-time eligibility; nominal product accuracy figures are not a guarantee of the resulting LEO forecast error.'),
'cutoffUtc':('The UTC forecast issue boundary: measurements are fitted up to this epoch and prediction begins here.','Choose an epoch with observation coverage. Cutoff-safe claims also depend on product issue-time evidence and on weather, EOP and attitude policies; selecting Forecast alone is not independent proof.'),
'products.mode':('Choose cutoff-restricted prediction or retrospective reconstruction.','Use Forecast with disclosed issue-time assumptions for prediction tests; Reconstruction allows later information and must be labelled accordingly.'),
'od.covarianceWeighting':('Use the position-fix covariance to weight measurement residuals instead of equal weights.','Keep the covariance/noise model physically meaningful; weighting does not itself guarantee smaller true errors or calibrated uncertainty.'),
'spacecraft.attitude':('Choose modelled nominal orientation or eligible measured PROQUA quaternions for box-wing forces.','Use documented attitude conventions and coverage. Do not assume nominal and measured attitudes agree to a fixed angle for every date.'),
'forces.dragModel':('Choose the implemented atmospheric density model used by drag.','Compare on matched held-out dates and consistent drivers. A model is not universally best; storm response alone does not establish improved forecast accuracy.'),
'forces.thirdBodyEphemeris':('Choose the Sun/Moon ephemeris source used by the force/environment setup.','Analytic needs no file; DE440 requires a compatible file. Neither is exact, and an improvement must be measured.'),
'inputs.de440File':('Compatible JPL planetary ephemeris data used when DE440 is selected.','Supply the proper data format and time coverage; the file is not needed for the analytic option.'),
'inputs.poeorb':('Independent reference files used by the nominal OD/forecast workflow for overlays and scoring.','Reference comparison does not feed back into nominal prediction. The separate Initial state → POEORB source deliberately uses a reference state as a propagation seed.'),
'weather.f107a':('81-day mean solar-flux driver used by the atmosphere; the constant is used if no weather file overrides it.','A retrospective centred average can contain future information. Forecast experiments must retain the configured cutoff-safe treatment and document which value was available.'),
'weather.forecastAp':('Assumed post-cutoff geomagnetic index for the selected persistence policy.','It is a scenario assumption, not a guaranteed storm forecast. Compare quiet and stressed scenarios without inserting future measured storm values.'),
'integrator.absTol':('Absolute error-control parameter for the numerical integrator.','Use convergence checks for the actual state scaling/problem. Numerical tolerance is not the total orbit prediction error or an accuracy guarantee.'),
'integrator.relTol':('Relative error-control parameter for the numerical integrator.','Lower usually costs more computation; verify numerical convergence separately from model and measurement errors.'),
'forecast.outputStepSeconds':('Spacing of stored ephemeris samples, not the adaptive internal integration step.','Choose adequate resolution for orbit users (often tens of seconds). Existing daily campaign OEMs are sparse benchmark exports and do not inherit the dense pipeline interpolation declaration.'),
}
lines=['# Complete GUI screenshot and control guide','',
'Version audited: '+subprocess.check_output(['git','rev-parse','--short','HEAD'],cwd=ROOT,text=True).strip()+'. Screenshots captured in Chromium on Linux; Windows native file/dialog appearance can differ. The application and screenshots run locally.','',
'## Coverage and how to use this guide','',
'This manual covers all seven application tabs, the shared toolbar, file browsers, conditionally visible fields, sweeps, comparison panels, station controls, and the separate interactive Guide page. It documents **85 parameter definitions** and **124 other controls/interactions**. Each fixed dropdown value is listed, including choices not currently selected. User preset names, catalog file names, filesystem roots and local run/window lists are dynamic rather than finite built-in choices.','',
'Every parameter key was observed in a rendered conditional configuration, except `workflow`, which is represented by the top two-button toggle and is documented explicitly. The audit records source hashes, live control lists and screenshot captions; the coverage check requires no missing parameter keys.','',
'**Data provenance:** Saved presentation screenshots show the actual archived two-month campaign. Populated Results, History and comparison screenshots use a separately created **SIMULATED** documentation dataset (SIMSAT, 15 January 2025), with short computed examples. The Data captures include clearly named simulated files and, in a supplemental capture, actual archived Sentinel inputs. The Window screenshot uses a clearly labelled one-date documentation record pointing to that simulated run, not a newly executed window campaign. These examples reveal controls and are not Sentinel-3A performance evidence. The original two-month campaign was not rerun.','',
'**Presentation shortcut:** open `gui --presentation`, stay on Saved presentation, select an experiment/date and explain the recorded results. Run, Run whole window, Start sweep, Compare product tiers and Create demo dataset start new work. Viewing saved data and downloading stored OEMs do not run orbit determination or propagation.','',
'## Suggested presentation order','',
'1. Saved presentation: show the real 61-date campaign and an issued/rejected case.\n2. Data: explain input categories, coverage and provenance.\n3. Run: explain workflows, cutoff, product eligibility and the collapsed settings groups.\n4. Results: explain an already saved local example, the view controls and formal uncertainty.\n5. Window and History: distinguish local records from the bundled archive.\n6. Help and Guide: show offline search and parameter explanations.\n7. End with OEM downloads and their units/frame/time conventions.','',
'## Screenshot atlas','']
for s in evidence['screenshots']:
 lines.extend(['### '+Path(s['file']).stem,'',s['caption'],'','!['+s['caption']+']('+s['file']+')',''])
lines+=['## Every control outside the parameter form','']
for section in dict.fromkeys(c['section'] for c in controls):
 lines+=['### '+section,'']
 for c in [x for x in controls if x['section']==section]:
  lines+=['#### '+c['id']+' — '+c['name'],'', '**Control:** '+c['kind']+'. **Visible when:** '+c['condition']+'.','',c['explanation'],'']
  if c['options']:lines+=['**All choices:** '+ '; '.join('`'+o+'`' for o in c['options'])+'.','']
lines+=['## Complete Run parameter reference — all 85 definitions','',
'For each field: key, type, unit, startup default, visibility rule, meaning, selection advice, bounds/preset shortcuts and every enum value. Defaults are the audited startup defaults, not an assertion that every stored experiment used them. Generic, imported and reused settings can differ. Field bounds are browser-form constraints; server checks and scientific validity can impose additional requirements.','']
for group in params['groups']:
 lines+=['### '+group,'']
 for p in [x for x in params['params'] if x['group']==group]:
  key=p['key'];what,how=corrections.get(key,(p['what'],p.get('how','')))
  # Treat heuristic magnitudes as indicative, not promises.
  vis='Both workflows' if not p.get('workflows') else 'Workflow: '+', '.join(p['workflows'])
  if p.get('showIf'):vis+='; also '+str(p['showIf'][0])+' '+('is not '+str(p['showIf'][1])[1:] if str(p['showIf'][1]).startswith('!') else 'equals '+str(p['showIf'][1]))
  val=get(key)
  dv=json.dumps(val,ensure_ascii=False)
  lines+=['#### '+p['label']+' — `'+key+'`','',
   '**Type:** '+p['type']+'. **Unit:** '+(p.get('unit') or 'none / see explanation')+'. **Startup default:** `'+dv+'`.','',
   '**Visibility:** '+vis+'.','',what,'','**How to choose/use:** '+how,'']
  if 'min'in p or 'max'in p:lines+=['**Form bounds:** '+str(p.get('min','not specified'))+' to '+str(p.get('max','not specified'))+'.','']
  if p.get('presets'):lines+=['**Shortcut buttons:** '+', '.join(map(str,p['presets']))+'. These set the field immediately.','']
  if p['type']=='bool':lines+=['**Choices:** on enables this behavior; off disables it. The explanation above describes what changes; switching some forces off hides their dependent fields.','']
  if p.get('options'):
   lines+=['**Every dropdown/toggle choice:**','']
   for value,label in p['options']:
    note=option_notes.get(key,{}).get(value)
    assert note,('Missing option explanation',key,value)
    lines+=['- **'+label+'** (`'+value+'`): '+note]
   lines+=['']
  if p['type'] in ('file','files'):
   lines+=['**Input widgets:** selected path(s) with remove ×; Add from catalog dropdown; Browse button. '+('Multiple files/folders can be accumulated.' if p['type']=='files' else 'A single file replaces the prior selection.')+' Allowed catalog kinds: '+', '.join(p.get('kinds',[]))+'.','']
lines+=['## Status, diagnostics and scientific interpretation','',
'- **ISSUED / SUCCESS:** an output exists; this does not mean it meets the accuracy target.\n- **FIT_REJECTED / OD_NOT_CONVERGED / FAILED_OD:** do not invent an ephemeris or count the case as zero error. The archive and live pipeline use different status vocabularies.\n- **FAILED_VALIDATION / FAILED_SPP / CANCELLED / ERROR:** inspect saved errors/logs; a failed record may have no trajectory/OEM.\n- **NO_DATA in Window:** inputs were unavailable/invalid for that cutoff; separate it from completed fitting failures.\n- **Post-fit RMS:** fit of the observations within the estimation arc, not the future orbit error.\n- **Variance factor:** checks assumed noise against residual scatter; model mismatch or maneuvers can also affect it.\n- **Formal covariance / 1σ:** uncertainty under the assumed measurement and dynamics model, not guaranteed real forecast error.\n- **PDOP:** geometric dilution; smaller is generally stronger geometry, but it is not a direct measured position error.\n- **Reference error:** compare independent states at the same epoch, frame and units. Missing reference coverage means unscored.\n- **Run-to-run difference:** separation from the selected base, not proof of which run is closer to truth.\n- **Forecast vs reconstruction:** disclose issue-time evidence and future weather/EOP/attitude policy. A retrospective model comparison is not an as-issued prediction.','',
'## Important distinctions visible in the GUI','',
'The procedural spacecraft drawing selector changes only the visualization; Run → Spacecraft → Shape controls the force model. Earth-fixed/Inertial changes the globe view; Forecast & output → OEM reference frame controls exported coordinates. Output step controls stored sampling; Integrator min/max step controls internal numerical integration. Pseudorange sigma weights observations; post-fit RMS measures residual fit; neither is the measured seven-day prediction error.','',
'The archived two-month OEMs have daily samples and no covariance. The separate short simulated Results example has dense samples and formal covariance to illustrate playback and ellipsoid controls. Do not present the simulated example’s small errors as achieved Sentinel-3A accuracy.','',
'The built-in interactive guide contains historical narrative, including an old statement that no IGS products are in the repository. That statement is superseded by the actual current archive and Saved presentation. This manual uses source-level controls and current captures rather than repeating that old campaign claim.','',
'## Completeness and reproducibility','',
'The finite parameter set is taken from `src/main/resources/gui/params.json`; non-parameter interactions were inspected in index.html, app.js, data.js, run.js, results.js, windowview.js, history.js, compare.js, help.js, ui.js, presentation.js, globe.js, map2d.js, spacecraft.js and guide/index.html/guide.js. `capture-evidence.json` stores the visible control inventory and dropdown lists for each capture. `coverage-audit.json` records the machine-checkable counts and source hashes. Native browser/OS prompts and file dialogs are described in text; their appearance varies by platform.','',
'No production settings, saved two-month results, or existing user workspace were changed to make the screenshots. Documentation scripts are included; recapturing populated Results needs an isolated saved example. Open README.html offline for search/navigation, or use the PDF/Word version for submission.','']
md='\n'.join(lines);(P/'GUI_COMPLETE_GUIDE.md').write_text(md)
# Audit against every finite parameter and enum choice.
allkeys={p['key'] for p in params['params']};seen=set(evidence['seenParameterKeys'])
assert allkeys<=seen,evidence.get('missingParameterKeys')
for p in params['params']:
 assert '`'+p['key']+'`' in md
 for value,label in p.get('options',[]):assert '`'+value+'`' in md
sources=['index.html','params.json']+['js/'+f+'.js' for f in ['app','data','run','results','history','windowview','help','compare','ui','presentation','globe','map2d','spacecraft']]+['guide/index.html','guide/guide.js']
audit={'parameter_definitions':len(allkeys),'documented_parameter_keys':sorted(allkeys),'rendered_parameter_keys_or_workflow_toggle':sorted(seen),'missing_parameter_keys':sorted(allkeys-seen),'non_parameter_controls':len(controls),'enum_definitions':sum(bool(p.get('options')) for p in params['params']),'enum_choices':sum(len(p.get('options',[])) for p in params['params']),'screenshots':len(evidence['screenshots']),'browser_errors':evidence['browserErrors'],'capture_provenance':'Real saved campaign in Saved presentation; separate simulated documentation examples elsewhere; one-date window illustration links to simulated run.','sources':{f:hashlib.sha256((ROOT/'src/main/resources/gui'/f).read_bytes()).hexdigest() for f in sources}}
(P/'coverage-audit.json').write_text(json.dumps(audit,indent=2)+'\n')
(P/'parameter-reference.json').write_text(json.dumps([dict(p,startup_default=get(p['key'])) for p in params['params']],indent=2,ensure_ascii=False)+'\n')
print('Manual generated:',len(md.split()),'words;',len(evidence['screenshots']),'screenshots')
