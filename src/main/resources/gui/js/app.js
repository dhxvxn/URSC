import { api, events } from './api.js';
import { toast } from './ui.js';
import { renderData } from './data.js';
import { renderRun } from './run.js';
import { renderResults, openRun } from './results.js';
import { renderHistory } from './history.js';
import { renderWindow } from './windowview.js';
import { renderHelp, search } from './help.js';

/** Shared application state and a tiny event bus. */
export const state = {
  params: null, defaults: null, catalog: { entries: [], watched: [], kinds: [] }, runs: [], job: null,
  listeners: {},
  on(evt, fn) { (this.listeners[evt] ||= []).push(fn); },
  emit(evt, data) { (this.listeners[evt] || []).forEach((fn) => fn(data)); },
};

export function showTab(name) {
  document.querySelectorAll('#tabs button').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
  document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t.id === 'tab-' + name));
  state.emit('tab', name);
}

export async function refreshCatalog() {
  state.catalog = await api('catalog');
  state.emit('catalog', state.catalog);
}

export async function refreshRuns() {
  state.runs = (await api('runs')).runs;
  state.emit('runs', state.runs);
}

function jobUi(evt) {
  const box = document.getElementById('job');
  if (evt.type === 'progress' || evt.type === 'started') {
    box.classList.remove('hidden');
    box.querySelector('.job-bar div').style.width = Math.round((evt.fraction || 0) * 100) + '%';
    box.querySelector('.job-text').textContent = evt.stage || 'starting';
  } else if (evt.type === 'done') {
    box.classList.add('hidden');
  }
}

async function main() {
  const theme = localStorage.getItem('ursc-theme');
  if (theme) document.documentElement.dataset.theme = theme;
  document.getElementById('theme').addEventListener('click', () => {
    const t = document.documentElement.dataset.theme === 'light' ? 'dark' : 'light';
    document.documentElement.dataset.theme = t;
    localStorage.setItem('ursc-theme', t);
  });
  document.getElementById('quit').addEventListener('click', async () => {
    if (!confirm('Stop the URSC server? Running jobs are cancelled.')) return;
    await api('shutdown', { method: 'POST' });
    document.body.innerHTML = '<p style="padding:40px">URSC has stopped. You can close this tab.</p>';
  });
  document.querySelectorAll('#tabs button').forEach((b) => b.addEventListener('click', () => showTab(b.dataset.tab)));
  document.getElementById('quick-search').addEventListener('submit', (e) => {
    e.preventDefault();
    showTab('help');
    search(document.getElementById('quick-search-q').value);
  });

  try {
    [state.params, state.defaults] = await Promise.all([
      fetch('params.json').then((r) => r.json()), api('config/default')]);
  } catch (e) {
    document.body.innerHTML = '<p style="padding:40px">Cannot reach the URSC server: ' + e.message
      + '. Open the exact URL printed when URSC started (it contains the session token).</p>';
    return;
  }
  await Promise.all([refreshCatalog(), refreshRuns()]);

  renderData(document.getElementById('tab-data'));
  renderRun(document.getElementById('tab-run'));
  renderResults(document.getElementById('tab-results'));
  renderHistory(document.getElementById('tab-history'));
  renderWindow(document.getElementById('tab-window'));
  renderHelp(document.getElementById('tab-help'));

  events(async (evt) => {
    jobUi(evt);
    state.emit('event', evt);
    if (evt.type === 'catalog') {
      const n = evt.added.length;
      if (n) toast(n + ' file' + (n > 1 ? 's' : '') + ' added to the catalog: ' + evt.added.map((e) => e.name).slice(0, 3).join(', ')
        + (n > 3 ? '…' : ''), 'ok', 8000);
      await refreshCatalog();
    } else if (evt.type === 'indexing') {
      toast('Indexing project data: ' + evt.folder + ' (first start; this takes a minute)', 'ok', 8000);
    } else if (evt.type === 'window-progress') {
      state.window = evt.window;
    } else if (evt.type === 'window-done') {
      state.window = null;
      await refreshRuns();
      toast('Window run finished' + (evt.cancelled ? ' (cancelled)' : ''), 'ok', 9000);
    } else if (evt.type === 'done' && state.window) {
      await refreshRuns();
    } else if (evt.type === 'done' && state.sweep && state.sweep.includes(evt.id)) {
      await refreshRuns();
      toast('Sweep run ' + evt.id + ': ' + evt.status, evt.status === 'SUCCESS' ? 'ok' : 'bad');
    } else if (evt.type === 'sweep') {
      state.sweep = null;
      await refreshRuns();
      const done = evt.ids.filter((id) => state.runs.some((r) => r.id === id && r.status === 'SUCCESS'));
      toast('Sweep of ' + evt.key + ' finished' + (evt.cancelled ? ' (cancelled)' : '') + ': ' + done.length + ' successful run(s)', 'ok', 9000);
      if (done.length >= 2) { showTab('history'); state.emit('compare', done); }
    } else if (evt.type === 'done') {
      await refreshRuns();
      const ok = evt.status === 'SUCCESS';
      toast('Run ' + evt.id + ' finished: ' + evt.status, ok ? 'ok' : 'bad', 8000);
      showTab('results');
      openRun(evt.id);
    } else if (evt.type === 'demo') {
      if (evt.status === 'ok') {
        toast('Simulated demo dataset ready in ' + evt.folder + '. Run tab: cutoff ' + evt.cutoffUtc + ' UTC, then Auto-select.', 'ok', 12000);
        state.emit('demo', evt);
        await refreshCatalog();
      } else toast('Demo dataset failed: ' + evt.message, 'bad');
    } else if (evt.type === 'error') {
      toast(evt.message, 'warn');
    }
  });
}

main();
