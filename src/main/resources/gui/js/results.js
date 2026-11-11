import { api, apiUrl } from './api.js';
import { el, toast, shortUtc } from './ui.js';
import { state, showTab } from './app.js';
import { Globe } from './globe.js';
import { GroundMap } from './map2d.js';
import { lineChart } from './charts.js';
import { loadConfig } from './run.js';
import { MODELS, modelFor } from './spacecraft.js';

let borders = null;
let globe = null, map = null;
let cur = null; // {id, result, config, traj, ref}
let playing = false, speed = 600, lastT = 0;

const picker = el('select', {});
const summary = el('div', { class: 'card' }, el('div', { class: 'muted' }, 'No run selected.'));
const vizCard = el('div', { class: 'card hidden' });
const plotsCard = el('div', { class: 'card hidden' });
const detailsCard = el('div', { class: 'card hidden' });
const slider = el('input', { type: 'range', min: 0, max: 0, step: 0.01, value: 0 });
const playBtn = el('button', { class: 'btn' }, '▶');
const speedSel = el('select', {}, ...[[60, '60×'], [300, '300×'], [600, '600×'], [1800, '1800×'], [3600, '3600×']].map(([v, l]) => el('option', { value: v, selected: v === 600 }, l)));
const readout = el('span', { class: 'mono small' });
const overlayText = el('div', { class: 'overlay' });
const ellBox = el('input', { type: 'checkbox' });
const ellLabel = el('span', { class: 'small muted' });
const ellToggle = el('label', { class: 'small', title: 'Formal 1σ position uncertainty, enlarged so it is visible at globe scale' }, ellBox, ' 1σ ellipsoid ', ellLabel);
const passesCard = el('div', { class: 'card' });
const modelSel = el('select', { title: 'Spacecraft model drawn on the globe (enlarged so it is visible)' },
  el('option', { value: 'auto' }, 'Model: automatic'),
  ...Object.entries(MODELS).map(([k, m]) => el('option', { value: k }, 'Model: ' + m.label)),
  el('option', { value: 'none' }, 'Model: dot only'));
const followBtn = el('button', { title: 'Camera rides with the satellite; drag to look around it, scroll to zoom' }, '🛰 Follow satellite');
const fullBtn = el('button', { title: 'Show the globe over the whole window (Esc to leave)' }, '⛶ Full view');
let stations = [];

export function renderResults(root) {
  const globeBox = el('div', { class: 'globe' }, overlayText,
    el('div', { class: 'viewsel toggle' },
      el('button', { class: 'on', onclick: (e) => setView('ecef', e.target) }, 'Earth-fixed'),
      el('button', { onclick: (e) => setView('inertial', e.target) }, 'Inertial')),
    el('div', { class: 'modelsel row' }, modelSel, el('div', { class: 'toggle' }, followBtn, fullBtn)));
  const setFull = (on) => { document.body.classList.toggle('globe-max', on); fullBtn.classList.toggle('on', on); fullBtn.textContent = on ? '✕ Leave full view' : '⛶ Full view'; };
  fullBtn.addEventListener('click', () => setFull(!document.body.classList.contains('globe-max')));
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && document.body.classList.contains('globe-max')) setFull(false); });
  modelSel.addEventListener('change', applyModel);
  followBtn.addEventListener('click', () => { const on = !followBtn.classList.contains('on'); followBtn.classList.toggle('on', on); globe.setFollow(on); });
  const mapCanvas = el('canvas', { class: 'map2d' });
  vizCard.append(
    el('div', { class: 'viz' }, globeBox, el('div', {}, mapCanvas,
      el('div', { class: 'kv small', style: 'margin-top:10px', id: 'viz-kv' }),
      el('p', { class: 'muted small' }, 'Drag the globe to rotate, scroll to zoom, double-click to reset. Purple: POEORB reference (display only).'))),
    el('div', { class: 'timebar' }, playBtn, speedSel, slider, readout, ellToggle));
  ellBox.addEventListener('change', () => {
    const f = globe.setUncertainty(ellBox.checked);
    ellLabel.textContent = ellBox.checked && f ? '(×' + Math.round(f).toLocaleString() + ')' : '';
  });
  root.append(el('h2', {}, 'Results'),
    el('div', { class: 'card' }, el('div', { class: 'row' }, el('b', {}, 'Run'), picker,
      el('button', { class: 'btn', onclick: () => cur && window.open(apiUrl('runs/' + cur.id + '/oem')) }, '⬇ Download OEM'),
      el('button', { class: 'btn', onclick: () => cur && window.open(apiUrl('runs/' + cur.id + '/log')) }, 'Log'),
      el('button', { class: 'btn', onclick: () => { if (!cur) return; loadConfig(cur.config); showTab('run'); toast('Settings of ' + cur.id + ' loaded into the Run tab'); } }, 'Re-use settings'),
      el('button', { class: 'btn', title: 'Google Earth track', onclick: () => cur && window.open(apiUrl('runs/' + cur.id + '/kml')) }, 'KML'),
      el('button', { class: 'btn', title: 'Cesium track', onclick: () => cur && window.open(apiUrl('runs/' + cur.id + '/czml')) }, 'CZML'),
      el('button', { class: 'btn', title: 'Printable report; use the browser\'s Print to save a PDF', onclick: () => cur && window.open(apiUrl('runs/' + cur.id + '/report')) }, '📄 Report'))),
    summary, vizCard, passesCard, plotsCard, detailsCard);
  loadStations();
  picker.addEventListener('change', () => picker.value && openRun(picker.value));
  slider.addEventListener('input', () => setTime(+slider.value));
  playBtn.addEventListener('click', () => { playing = !playing; playBtn.textContent = playing ? '❚❚' : '▶'; lastT = performance.now(); if (playing) requestAnimationFrame(tick); });
  speedSel.addEventListener('change', () => { speed = +speedSel.value; });
  globe = new Globe(globeBox);
  map = new GroundMap(mapCanvas);
  fetch('vendor/borders-50m.json').then((r) => r.json()).then((b) => { borders = b.lines; globe.setBorders(borders); map.setBorders(borders); });
  const fill = () => {
    picker.replaceChildren(el('option', { value: '' }, '— choose a run —'), ...state.runs.map((r) =>
      el('option', { value: r.id, selected: cur && r.id === cur.id }, r.id + '  ' + (r.runName || '') + '  ' + r.status + (r.objectName ? '  ' + r.objectName : ''))));
  };
  state.on('runs', fill);
  fill();
}

function objectName() {
  return (cur && (cur.result.objectName || (cur.config.spacecraft && cur.config.spacecraft.objectName))) || '';
}

function applyModel() {
  const v = modelSel.value;
  const key = v === 'auto' ? modelFor(objectName()) : v;
  globe.setModel(key === 'none' ? null : key);
  modelSel.options[0].textContent = 'Model: automatic (' + (MODELS[modelFor(objectName())].label) + ')';
}

function setView(v, btn) {
  btn.parentElement.querySelectorAll('button').forEach((b) => b.classList.toggle('on', b === btn));
  globe.setView(v);
}

function tick(now) {
  if (!playing || !cur || !cur.traj) return;
  const dt = (now - lastT) / 1000;
  lastT = now;
  const t = cur.traj.t;
  const step = t.length > 1 ? t[1] - t[0] : 60;
  let f = +slider.value + dt * speed / step;
  if (f >= t.length - 1) f = 0;
  setTime(f);
  requestAnimationFrame(tick);
}

function setTime(f) {
  slider.value = f;
  globe.setTime(f);
  map.setTime(f);
  const t = cur.traj;
  const i = Math.min(t.t.length - 1, Math.round(f));
  readout.textContent = shortUtc(t.epochs[i]) + ' UTC  (+' + (t.t[i] / 3600).toFixed(2) + ' h)';
  overlayText.textContent = (cur.result.objectName || 'satellite') + '  ' + shortUtc(t.epochs[i]) + ' UTC';
  const kv = document.getElementById('viz-kv');
  const rows = [['Latitude', t.lat[i].toFixed(3) + '°'], ['Longitude', t.lon[i].toFixed(3) + '°'], ['Altitude', t.alt[i].toFixed(3) + ' km']];
  if (t.sigmaRtn && t.sigmaRtn[i]) rows.push(['1σ R / T / N', t.sigmaRtn[i].map((v) => v.toFixed(2)).join(' / ') + ' m']);
  if (cur.ref && cur.ref.err3d) {
    const k = cur.ref.runIndex.indexOf(i);
    if (k >= 0) rows.push(['Error vs POEORB', cur.ref.err3d[k].toFixed(2) + ' m']);
  }
  kv.replaceChildren(...rows.flatMap(([a, b]) => [el('div', {}, a), el('div', { class: 'mono' }, b)]));
}

export async function openRun(id) {
  let d;
  try { d = await api('runs/' + id); } catch (e) { toast(e.message, 'bad'); return; }
  cur = { id, result: d.result, config: d.config, traj: null, ref: null };
  picker.value = id;
  drawSummary();
  vizCard.classList.add('hidden');
  plotsCard.classList.add('hidden');
  if (d.result.states > 0) {
    try { cur.traj = await (await fetch(apiUrl('runs/' + id + '/trajectory'))).json(); } catch (e) { toast('No trajectory: ' + e.message, 'warn'); }
    try { const r = await fetch(apiUrl('runs/' + id + '/reference')); if (r.ok) cur.ref = await r.json(); } catch { /* optional */ }
  }
  if (cur.traj) {
    vizCard.classList.remove('hidden');
    slider.max = cur.traj.t.length - 1;
    globe.setTrajectory(cur.traj, cur.ref && cur.ref.track);
    map.setTrajectory(cur.traj, cur.ref && cur.ref.track);
    applyModel();
    ellToggle.classList.toggle('hidden', !cur.traj.sigmaRtn);
    if (!cur.traj.sigmaRtn) ellBox.checked = false;
    ellBox.dispatchEvent(new Event('change'));
    setTime(0);
  }
  drawPasses();
  drawPlots();
  drawDetails();
}

function badge(status) {
  return el('span', { class: 'badge ' + (status === 'SUCCESS' ? 'ok' : 'bad') }, status);
}

function drawSummary() {
  const r = cur.result;
  const od = r.od;
  const kv = [];
  const add = (k, v) => { if (v !== undefined && v !== null && v !== '') kv.push(el('div', {}, k), el('div', {}, v)); };
  add('Status', badge(r.status));
  add('Mode', r.label);
  add('Object', [r.objectName, r.objectId].filter(Boolean).join('  '));
  add(r.workflow === 'PROPAGATE' ? 'Initial epoch' : 'UTC cutoff', shortUtc(r.cutoffUtc));
  add('Output states', r.states + (r.hasCovariance ? ' (with covariance)' : ''));
  if (od) {
    add('Orbit determination', od.observations + ' SPP fixes, ' + (od.weighted ? 'covariance-weighted' : 'unit-weighted') + ', ' + od.iterations + ' iterations, ' + (od.converged ? 'converged' : 'NOT converged'));
    add('Post-fit RMS', od.postFitRms.toFixed(3) + ' m (pre-fit ' + od.preFitRms.toFixed(1) + ' m)');
    if (od.varianceFactor != null) add('Variance factor', od.varianceFactor.toFixed(3) + (od.varianceFactor > 3 ? '  — pseudorange sigma looks too small' : od.varianceFactor < 0.2 ? '  — pseudorange sigma looks too large' : ''));
    if (od.parameterNames) {
      const extra = od.parameterNames.map((n, i) => [n, od.parameters[i]]).filter(([n]) => n === 'Cd' || n === 'Cr');
      extra.forEach(([n, v]) => add('Estimated ' + n, v.toFixed(4) + (od.covariance ? ' ± ' + Math.sqrt(od.covariance[od.parameterNames.indexOf(n)][od.parameterNames.indexOf(n)]).toFixed(4) : '')));
    }
  }
  const list = (items) => el('ul', { style: 'margin:0;padding-left:18px' }, ...items.map((x) => el('li', {}, x)));
  const usage = Object.entries(r.productUsage || {});
  if (usage.length) add('GPS products used', list(usage.map(([k, v]) => k + ' — ' + v + ' epochs')));
  if ((r.productRejected || []).length) add('Products not used', list(r.productRejected));
  const fb = Object.entries(r.productFallbacks || {});
  if (fb.length) add('Fallbacks', list(fb.map(([k, v]) => k + ' (' + v + ')')));
  if ((r.defaultsUsed || []).length) add('Defaults used', list(r.defaultsUsed));
  if ((r.warnings || []).length) add('Warnings', list(r.warnings));
  if ((r.errors || []).length) add('Errors', el('div', { style: 'color:var(--bad)' }, list(r.errors)));
  add('Run time', (r.elapsedSeconds || 0).toFixed(1) + ' s');
  const flags = (r.flags || []).length ? el('div', { class: 'flagbox' }, el('b', {}, '⚠ Check this run'),
    el('ul', { style: 'margin:4px 0 0;padding-left:18px' }, ...r.flags.map((f) => el('li', {}, f)))) : '';
  summary.replaceChildren(el('h3', {}, 'Run ' + cur.id + (r.runName ? ' — ' + r.runName : '')), flags, el('div', { class: 'kv' }, ...kv));
}

function drawPlots() {
  const charts = [];
  const t = cur.traj;
  const r = cur.result;
  if (t) {
    const hrs = t.t.map((s) => s / 3600);
    charts.push(['Altitude', { x: hrs, xLabel: 'h', yUnit: 'km', series: [{ name: 'altitude', y: t.alt, color: '#4f9cf7' }] }]);
    if (t.sigmaRtn) {
      charts.push(['Formal 1σ uncertainty', { x: hrs, xLabel: 'h', yUnit: 'm', yMin: 0, series: [
        { name: 'radial', y: t.sigmaRtn.map((s) => s && s[0]), color: '#3fbf74' },
        { name: 'along-track', y: t.sigmaRtn.map((s) => s && s[1]), color: '#e0a44a' },
        { name: 'cross-track', y: t.sigmaRtn.map((s) => s && s[2]), color: '#a583f0' }] }]);
    }
    if (cur.ref && cur.ref.err3d) {
      const x = cur.ref.runIndex.map((i) => t.t[i] / 3600);
      charts.push(['Error vs POEORB (display only)', { x, xLabel: 'h', yUnit: 'm', series: [
        { name: '3D', y: cur.ref.err3d, color: '#ef6b5b', width: 2 },
        { name: 'radial', y: cur.ref.radial, color: '#3fbf74' },
        { name: 'along', y: cur.ref.along, color: '#e0a44a' },
        { name: 'cross', y: cur.ref.cross, color: '#a583f0' }] }]);
    }
  }
  if (r.spp && r.spp.length) {
    const t0 = Date.parse(r.spp[0].utc.replace(/\.\d+$/, '') + 'Z');
    const x = r.spp.map((s) => (Date.parse(s.utc.replace(/\.\d+$/, '') + 'Z') - t0) / 3600e3);
    charts.push(['GNSS positioning: geometry', { x, xLabel: 'h into arc', series: [
      { name: 'satellites', y: r.spp.map((s) => s.satellites), color: '#4f9cf7' },
      { name: 'PDOP', y: r.spp.map((s) => s.pdop), color: '#e0a44a' }] }]);
    charts.push(['GNSS positioning: residual RMS', { x, xLabel: 'h into arc', yUnit: 'm', yMin: 0, series: [
      { name: 'accepted', y: r.spp.map((s) => (s.accepted ? s.rms : null)), color: '#3fbf74', points: true },
      { name: 'rejected', y: r.spp.map((s) => (!s.accepted ? s.rms : null)), color: '#ef6b5b', points: true }] }]);
  }
  if (r.od && r.od.residualNorm && r.od.residualNorm.length) {
    const t0 = Date.parse(r.od.residualUtc[0].replace(/\.\d+$/, '') + 'Z');
    const x = r.od.residualUtc.map((s) => (Date.parse(s.replace(/\.\d+$/, '') + 'Z') - t0) / 3600e3);
    charts.push(['OD post-fit residuals', { x, xLabel: 'h into arc', yUnit: 'm', yMin: 0, series: [{ name: '|residual|', y: r.od.residualNorm, color: '#ec6bb0', points: true }] }]);
  }
  if (!charts.length) return;
  plotsCard.classList.remove('hidden');
  const grid = el('div', { class: 'grid2' });
  plotsCard.replaceChildren(el('h3', {}, 'Plots'), grid);
  for (const [title, opts] of charts) {
    const cv = el('canvas', { class: 'chart' });
    grid.append(el('div', {}, cv));
    requestAnimationFrame(() => lineChart(cv, Object.assign({ title }, opts)));
  }
}

function drawDetails() {
  const r = cur.result;
  if (!r.spp || !r.spp.length) { detailsCard.classList.add('hidden'); return; }
  detailsCard.classList.remove('hidden');
  const acc = r.spp.filter((s) => s.accepted).length;
  detailsCard.replaceChildren(el('details', {},
    el('summary', {}, el('b', {}, 'GNSS positioning per epoch'), ' — ' + acc + ' of ' + r.spp.length + ' accepted'),
    el('div', { class: 'table-wrap', style: 'margin-top:8px' }, el('table', {},
      el('thead', {}, el('tr', {}, ...['Epoch (UTC)', 'GPS product', 'Sats', 'PDOP', 'RMS [m]', 'Used', 'Reason'].map((h) => el('th', {}, h)))),
      el('tbody', {}, ...r.spp.map((s) => el('tr', {}, el('td', { class: 'mono small' }, shortUtc(s.utc)),
        el('td', {}, (s.product || '') + (s.predictedOrbit ? ' (predicted)' : '')), el('td', {}, s.satellites),
        el('td', {}, s.pdop == null ? '' : s.pdop.toFixed(2)), el('td', {}, s.rms == null ? '' : s.rms.toFixed(2)),
        el('td', {}, s.accepted ? '✓' : '✕'), el('td', { class: 'small muted' }, s.reason))))))));
}

// ---- ground stations and passes ----
async function loadStations() {
  try { stations = (await api('stations')).stations; } catch { stations = []; }
  globe.setStations(stations);
  map.setStations(stations);
  drawPasses();
}

async function drawPasses() {
  const rows = stations.map((s) => ({ ...s }));
  const tbody = el('tbody');
  const num = (o, k, step) => el('input', { type: 'number', step, value: o[k], oninput: (e) => { o[k] = e.target.value === '' ? null : +e.target.value; } });
  const drawRows = () => tbody.replaceChildren(...rows.map((o, k) => el('tr', {},
    el('td', {}, el('input', { value: o.name || '', oninput: (e) => { o.name = e.target.value; } })),
    el('td', {}, num(o, 'latDeg', 'any')), el('td', {}, num(o, 'lonDeg', 'any')), el('td', {}, num(o, 'heightM', 'any')),
    el('td', {}, num(o, 'minElevationDeg', 'any')),
    el('td', {}, el('button', { class: 'btn small danger', onclick: () => { rows.splice(k, 1); drawRows(); } }, '✕')))));
  drawRows();
  const save = async () => {
    try {
      stations = (await api('stations', { method: 'POST', body: { stations: rows } })).stations;
      toast('Stations saved');
      globe.setStations(stations); map.setStations(stations);
      drawPasses();
    } catch (e) { toast(e.message, 'bad', 8000); }
  };
  const editor = el('details', { open: !stations.length },
    el('summary', {}, el('b', {}, 'Stations'), ' — ' + stations.length + ' defined (saved in the workspace, used by every run)'),
    el('div', { class: 'table-wrap', style: 'margin-top:8px' }, el('table', { class: 'stations' },
      el('thead', {}, el('tr', {}, ...['Name', 'Latitude [°]', 'Longitude [°]', 'Height [m]', 'Elevation mask [°]', ''].map((h) => el('th', {}, h)))), tbody)),
    el('div', { class: 'row', style: 'margin-top:8px' },
      el('button', { class: 'btn', onclick: () => { rows.push({ name: '', latDeg: 0, lonDeg: 0, heightM: 0, minElevationDeg: 5 }); drawRows(); } }, '+ Add station'),
      el('button', { class: 'btn primary', onclick: save }, 'Save stations')));
  let table = el('p', { class: 'muted small' }, stations.length ? 'Open a run with a trajectory to list its passes.' : 'Add a station to list its visibility passes.');
  if (cur && cur.traj && stations.length) {
    try {
      const p = (await api('runs/' + cur.id + '/passes')).passes;
      table = p.length ? el('div', { class: 'table-wrap' }, el('table', {},
        el('thead', {}, el('tr', {}, ...['Station', 'AOS (UTC)', 'LOS (UTC)', 'Duration', 'Max elevation'].map((h) => el('th', {}, h)))),
        el('tbody', {}, ...p.map((x) => el('tr', { class: 'clickable', title: 'Show on the globe', onclick: () => jumpTo(x.aos) },
          el('td', {}, x.station), el('td', { class: 'mono small' }, shortUtc(x.aosUtc)), el('td', { class: 'mono small' }, shortUtc(x.losUtc)),
          el('td', {}, (x.durationS / 60).toFixed(1) + ' min'), el('td', {}, x.maxElevationDeg.toFixed(1) + '°' + (x.truncated ? ' (cut by span)' : ''))))))) :
        el('p', { class: 'muted small' }, 'No passes above the elevation masks in this run.');
    } catch (e) { table = el('p', { class: 'small', style: 'color:var(--bad)' }, e.message); }
  }
  passesCard.replaceChildren(el('h3', {}, 'Ground-station passes'), editor, table);
}

function jumpTo(seconds) {
  if (!cur || !cur.traj) return;
  const t = cur.traj.t;
  let k = 0;
  while (k < t.length - 1 && t[k + 1] <= seconds) k++;
  setTime(k);
  document.querySelector('.globe').scrollIntoView({ behavior: 'smooth', block: 'center' });
}
