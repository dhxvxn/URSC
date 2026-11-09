import { api } from './api.js';
import { el, toast, shortUtc } from './ui.js';
import { state, refreshRuns, showTab } from './app.js';
import { openRun } from './results.js';
import { loadConfig } from './run.js';

export function renderHistory(root) {
  const tbody = el('tbody');
  const compareBox = el('div');
  root.append(el('h2', {}, 'Run history'),
    el('div', { class: 'card' },
      el('p', { class: 'muted small' }, 'Every run is kept with its settings, input hashes, outputs and log. Tick two or more runs and press Compare.'),
      el('div', { class: 'row', style: 'margin-bottom:8px' }, el('button', { class: 'btn', onclick: compare }, 'Compare selected')),
      el('div', { class: 'table-wrap' }, el('table', {}, el('thead', {}, el('tr', {},
        ...['', 'Run', 'Name', 'Workflow', 'Status', 'Mode', 'Cutoff / epoch', 'Object', 'States', 'Post-fit RMS', ''].map((h) => el('th', {}, h)))), tbody))),
    compareBox);
  const draw = () => {
    tbody.replaceChildren(...state.runs.map((r) => el('tr', {},
      el('td', {}, el('input', { type: 'checkbox', value: r.id, class: 'cmp' })),
      el('td', { class: 'mono small' }, r.id), el('td', {}, r.runName || ''), el('td', {}, r.workflow),
      el('td', {}, el('span', { class: 'badge ' + (r.status === 'SUCCESS' ? 'ok' : 'bad') }, r.status)),
      el('td', { class: 'small' }, r.label || ''), el('td', { class: 'small' }, shortUtc(r.cutoffUtc)), el('td', {}, r.objectName || ''),
      el('td', {}, r.states ?? ''), el('td', {}, r.postFitRms == null ? '' : (+r.postFitRms).toFixed(2) + ' m'),
      el('td', {}, el('div', { class: 'row', style: 'gap:4px' },
        el('button', { class: 'btn small', onclick: () => { showTab('results'); openRun(r.id); } }, 'Open'),
        el('button', { class: 'btn small', onclick: async () => { const d = await api('runs/' + r.id); loadConfig(d.config); showTab('run'); toast('Settings loaded'); } }, 'Re-use'),
        el('button', { class: 'btn small danger', onclick: async () => { if (!confirm('Delete run ' + r.id + ' and its files?')) return; await api('runs/' + r.id, { method: 'DELETE' }); refreshRuns(); } }, 'Delete'))))));
  };

  async function compare(_e, preset) {
    const ids = preset || [...root.querySelectorAll('.cmp:checked')].map((c) => c.value);
    if (ids.length < 2) { toast('Tick at least two runs', 'warn'); return; }
    try {
      const d = await api('compare?ids=' + ids.join(','));
      const { renderCompare } = await import('./compare.js');
      renderCompare(compareBox, d);
    } catch (e) { toast(e.message, 'bad'); }
  }
  state.on('compare', (ids) => compare(null, ids));
  state.on('runs', draw);
  draw();
}
