import { el, shortUtc } from './ui.js';
import { lineChart } from './charts.js';

const COLORS = ['#ef6b5b', '#4f9cf7', '#3fbf74', '#e0a44a', '#a583f0', '#ec6bb0', '#5fc7c7', '#c9c95a'];
const fmt = (v) => (v == null ? '' : Math.abs(v) >= 100 ? v.toFixed(0) : Math.abs(v) >= 1 ? v.toFixed(2) : v.toFixed(3));
const show = (v) => (v === null || v === undefined ? '—' : Array.isArray(v) ? v.join(', ') || '—' : String(v));

/** Comparison view: runs, settings that differ, and position differences relative to the first run. */
export function renderCompare(box, d) {
  const name = (id) => { const r = d.runs.find((x) => x.id === id); return (r && r.runName) || id; };
  const runRows = d.runs.map((r, k) => el('tr', {},
    el('td', {}, el('span', { class: 'swatch', style: 'background:' + (k === 0 ? 'var(--muted)' : COLORS[(k - 1) % COLORS.length]) })),
    el('td', { class: 'mono small' }, r.id + (k === 0 ? ' (base)' : '')), el('td', {}, r.runName || ''),
    el('td', {}, el('span', { class: 'badge ' + (r.status === 'SUCCESS' ? 'ok' : 'bad') }, r.status)),
    el('td', { class: 'small' }, r.label || ''), el('td', {}, r.postFitRms == null ? '' : (+r.postFitRms).toFixed(2) + ' m'),
    el('td', { class: 'small' }, (r.flags || []).length ? el('span', { class: 'badge warn', title: r.flags.join('\n') }, '⚠ ' + r.flags.length + ' flag(s)') : '')));
  const diffRows = d.diffs.map((x) => el('tr', {}, el('td', {}, name(x.id)),
    ...(x.error ? [el('td', { colspan: 4, style: 'color:var(--bad)' }, x.error)]
      : [el('td', {}, fmt(x.max3d)), el('td', {}, fmt(x.rms3d)), el('td', {}, fmt(x.final3d)), el('td', { class: 'small muted' }, x.frame)])));
  const settings = d.settings.length
    ? el('div', { class: 'table-wrap' }, el('table', {}, el('thead', {}, el('tr', {}, el('th', {}, 'Setting'), ...d.runs.map((r) => el('th', {}, r.runName || r.id)))),
      el('tbody', {}, ...d.settings.map((s) => el('tr', {}, el('td', { class: 'mono small' }, s.key),
        ...s.values.map((v, k) => el('td', { class: 'small' + (k > 0 ? ' changed' : '') }, show(v))))))))
    : el('p', { class: 'muted small' }, 'The runs have identical settings (apart from their names).');
  const charts = el('div', { class: 'grid2' });
  box.replaceChildren(el('div', { class: 'card' },
    el('h3', {}, 'Comparison — differences relative to ' + name(d.base)),
    el('div', { class: 'table-wrap' }, el('table', {}, el('thead', {}, el('tr', {}, ...['', 'Run', 'Name', 'Status', 'Mode', 'Post-fit RMS', 'Flags'].map((h) => el('th', {}, h)))), el('tbody', {}, ...runRows))),
    el('h4', {}, 'Position difference to the base run [m]'),
    el('div', { class: 'table-wrap' }, el('table', {}, el('thead', {}, el('tr', {}, ...['Run', 'Max 3D', 'RMS 3D', 'At end', 'Frame'].map((h) => el('th', {}, h)))), el('tbody', {}, ...diffRows))),
    el('h4', {}, 'Settings that differ'), settings,
    el('p', { class: 'muted small' }, 'Differences are other run minus base, in the base run\'s radial / along-track / cross-track frame. Epochs that do not coincide are interpolated (8-point Lagrange).'),
    charts));
  const ok = d.diffs.filter((x) => !x.error && x.t.length);
  if (!ok.length) return;
  const add = (title, opts) => { const cv = el('canvas', { class: 'chart' }); charts.append(el('div', {}, cv)); requestAnimationFrame(() => lineChart(cv, Object.assign({ title }, opts))); };
  // all runs share the base's time axis
  const ref = ok.reduce((a, b) => (b.t.length > a.t.length ? b : a));
  const x = ref.t.map((s) => s / 3600);
  const align = (r, key) => { const m = new Map(r.t.map((t, i) => [t, r[key][i]])); return ref.t.map((t) => (m.has(t) ? m.get(t) : null)); };
  add('3D difference vs base', { x, xLabel: 'h from ' + shortUtc(ref.firstEpoch), yUnit: 'm', yMin: 0,
    series: ok.map((r) => ({ name: name(r.id), y: align(r, 'err3d'), color: COLORS[(d.diffs.indexOf(r)) % COLORS.length], width: 2 })) });
  for (const r of ok.slice(0, 5)) {
    add('RTN difference: ' + name(r.id), { x, xLabel: 'h', yUnit: 'm', series: [
      { name: 'radial', y: align(r, 'radial'), color: '#3fbf74' },
      { name: 'along', y: align(r, 'along'), color: '#e0a44a' },
      { name: 'cross', y: align(r, 'cross'), color: '#a583f0' }] });
  }
  box.scrollIntoView({ behavior: 'smooth', block: 'start' });
}
