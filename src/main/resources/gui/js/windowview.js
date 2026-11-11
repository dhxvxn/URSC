import { api } from './api.js';
import { el, toast } from './ui.js';
import { state, showTab } from './app.js';
import { openRun } from './results.js';
import { lineChart } from './charts.js';

/** Window tab: one OD + forecast per day over a date range, with a per-day table and trends. */
let cur = null;          // full window summary
const picker = el('select', {});
const progress = el('div', { class: 'hidden' }, el('div', { class: 'job-bar', style: 'width:100%;height:8px' }, el('div')),
  el('div', { class: 'small muted', style: 'margin-top:4px' }));
const tiles = el('div', { class: 'tiles' });
const charts = el('div', { class: 'grid2' });
const tbody = el('tbody');
const intro = el('p', { class: 'muted small' },
  'A window run repeats orbit determination and a forecast for every cutoff in a date range, picking each day\'s observations and GPS products from the catalog. ',
  'Set the dates under Run → Test window and press "Run whole window". The tested Sentinel-3A window (3 Sep – 2 Nov 2024, 05:59:42 UTC) is the default.');

export function renderWindow(root) {
  root.append(el('h2', {}, 'Window runs'),
    el('div', { class: 'card' }, intro,
      el('div', { class: 'row' }, el('b', {}, 'Window'), picker,
        el('button', { class: 'btn', onclick: () => refresh() }, 'Refresh'),
        el('button', { class: 'btn danger', onclick: del }, 'Delete record')),
      progress),
    tiles,
    el('div', { class: 'card' }, el('h3', {}, 'Trends across the window'), charts),
    el('div', { class: 'card' }, el('h3', {}, 'Per-day results'),
      el('p', { class: 'muted small' }, 'Click a row to open that day in Results (globe, ground track, plots, OEM). Errors against POEORB appear only for days whose reference orbit is in the catalog; they are for display and never used by a run.'),
      el('div', { class: 'table-wrap' }, el('table', {}, el('thead', {}, el('tr', {},
        ...['Cutoff date', 'Status', 'GPS product', 'Fixes', 'Post-fit RMS', 'Var. factor', 'Flags', 'Err @cutoff', 'Err +1 d', 'Err +7 d', 'Run time'].map((h) => el('th', {}, h)))), tbody))));
  picker.addEventListener('change', () => picker.value && load(picker.value));
  state.on('event', onEvent);
  state.on('tab', (t) => { if (t === 'window' && !cur) refresh(); });
  refresh();
}

async function refresh(select) {
  const ws = (await api('windows')).windows;
  picker.replaceChildren(...(ws.length ? ws : [{ id: '', name: 'no window runs yet' }]).map((w) =>
    el('option', { value: w.id, selected: (select || (cur && cur.id)) === w.id },
      w.id ? w.name + '  ' + w.startDate + ' → ' + w.endDate + '  (' + w.done + '/' + w.count + (w.finished ? '' : ', running') + ')' : w.name)));
  const id = select || (cur && cur.id) || (ws[0] && ws[0].id);
  if (id) load(id); else draw();
}

async function load(id) {
  try { cur = await api('windows/' + id); } catch (e) { toast(e.message, 'bad'); return; }
  picker.value = id;
  draw();
}

async function del() {
  if (!cur || !confirm('Delete the window record ' + cur.id + '? The individual runs stay in History.')) return;
  await api('windows/' + cur.id, { method: 'DELETE' });
  cur = null;
  refresh();
}

function onEvent(evt) {
  if (evt.type === 'window-progress') {
    progress.classList.remove('hidden');
    progress.querySelector('.job-bar div').style.width = Math.round(evt.index / evt.count * 100) + '%';
    progress.querySelector('.small').textContent = 'Running cutoff ' + (evt.index + 1) + ' of ' + evt.count + ': ' + evt.date + '  (run ' + evt.id + ')';
    if (!cur || cur.id !== evt.window) refresh(evt.window);
  } else if (evt.type === 'progress' && state.window) {
    progress.querySelector('.small').textContent = progress.querySelector('.small').textContent.replace(/ — .*$/, '') + ' — ' + evt.stage;
  } else if (evt.type === 'window-day') {
    if (cur && cur.id === evt.window) {
      cur.days.push(evt.row);
      cur.done = cur.days.length;
      progress.querySelector('.job-bar div').style.width = Math.round((evt.index + 1) / evt.count * 100) + '%';
      draw();
    }
  } else if (evt.type === 'window-done') {
    progress.classList.add('hidden');
    refresh(evt.window);
  }
}

const fmt = (v, d = 2) => (v == null || Number.isNaN(+v) ? '' : (+v).toFixed(d));
const fmtM = (v) => (v == null ? '' : +v >= 1000 ? (+v / 1000).toFixed(2) + ' km' : (+v).toFixed(2) + ' m');

function median(a) {
  const s = a.filter((x) => x != null).map(Number).sort((x, y) => x - y);
  return s.length ? (s.length % 2 ? s[(s.length - 1) / 2] : (s[s.length / 2 - 1] + s[s.length / 2]) / 2) : null;
}

function draw() {
  if (!cur) {
    tiles.replaceChildren();
    charts.replaceChildren(el('p', { class: 'muted small' }, 'No window run selected.'));
    tbody.replaceChildren();
    return;
  }
  const d = cur.days || [];
  const ok = d.filter((r) => r.status === 'SUCCESS');
  const failed = d.filter((r) => r.status && r.status !== 'SUCCESS' && r.status !== 'NO_DATA');
  const noData = d.filter((r) => r.status === 'NO_DATA');
  const flagged = d.filter((r) => (r.flags || []).length);
  const tile = (label, value, cls = '') => el('div', { class: 'tile ' + cls }, el('div', { class: 'v' }, String(value)), el('div', { class: 'l' }, label));
  tiles.replaceChildren(el('div', { class: 'card tiles-row' },
    tile('cutoffs in window', cur.count),
    tile('processed', cur.done + (cur.finished ? '' : ' …')),
    tile('forecasts issued', ok.length, 'ok'),
    tile('OD not usable', failed.length, failed.length ? 'bad' : ''),
    tile('no data', noData.length, noData.length ? 'warn' : ''),
    tile('flagged', flagged.length, flagged.length ? 'warn' : ''),
    tile('median post-fit RMS', median(ok.map((r) => r.postFitRms)) == null ? '—' : fmt(median(ok.map((r) => r.postFitRms))) + ' m')));
  // per-day table
  tbody.replaceChildren(...d.map((r) => el('tr', { class: r.runId ? 'clickable' : '', title: r.runId ? 'Open in Results' : (r.note || ''),
    onclick: () => { if (r.runId) { showTab('results'); openRun(r.runId); } } },
  el('td', { class: 'mono small' }, r.date),
  el('td', {}, el('span', { class: 'badge ' + (r.status === 'SUCCESS' ? 'ok' : r.status === 'NO_DATA' ? 'warn' : 'bad') }, r.status === 'NO_DATA' ? 'no data' : r.status)),
  el('td', { class: 'small' }, (r.products || []).map((p) => p.replace(/ \(.*$/, '')).join(', ')),
  el('td', {}, r.fixes ?? ''), el('td', {}, r.postFitRms == null ? '' : fmt(r.postFitRms) + ' m'), el('td', {}, fmt(r.varianceFactor, 3)),
  el('td', {}, (r.flags || []).length ? el('span', { class: 'badge warn', title: r.flags.join('\n') }, '⚠ ' + r.flags.length) : ''),
  el('td', {}, fmtM(r.errCutoff)), el('td', {}, fmtM(r.err1d)), el('td', {}, fmtM(r.err7d)),
  el('td', { class: 'small' }, r.elapsedSeconds == null ? '' : fmt(r.elapsedSeconds, 0) + ' s'))));
  // trends
  charts.replaceChildren();
  if (!d.length) { charts.append(el('p', { class: 'muted small' }, 'Waiting for the first cutoff…')); return; }
  const x = d.map((r, i) => i * (cur.stepDays || 1));
  const add = (title, opts) => { const cv = el('canvas', { class: 'chart' }); charts.append(el('div', {}, cv)); requestAnimationFrame(() => lineChart(cv, Object.assign({ title }, opts))); };
  add('OD post-fit RMS per cutoff', { x, xLabel: 'days from ' + cur.startDate, yUnit: 'm', yMin: 0, series: [
    { name: 'issued', y: d.map((r) => (r.status === 'SUCCESS' ? r.postFitRms : null)), color: '#3fbf74', points: true },
    { name: 'flagged', y: d.map((r) => ((r.flags || []).length ? r.postFitRms : null)), color: '#e0a44a', points: true }] });
  add('SPP fixes used per cutoff', { x, xLabel: 'days from ' + cur.startDate, yMin: 0, series: [
    { name: 'fixes', y: d.map((r) => r.fixes ?? null), color: '#4f9cf7', points: true }] });
  if (d.some((r) => r.err1d != null || r.errCutoff != null)) {
    add('Error vs POEORB (days with a reference)', { x, xLabel: 'days from ' + cur.startDate, yUnit: 'm', yMin: 0, series: [
      { name: 'at cutoff', y: d.map((r) => r.errCutoff ?? null), color: '#3fbf74', points: true },
      { name: '+1 day', y: d.map((r) => r.err1d ?? null), color: '#e0a44a', points: true },
      { name: '+7 days', y: d.map((r) => r.err7d ?? null), color: '#ef6b5b', points: true }] });
  }
}
