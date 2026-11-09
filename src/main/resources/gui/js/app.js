import { api, events } from './api.js';
import { toast } from './ui.js';
import { renderData } from './data.js';
import { renderRun } from './run.js';
import { renderResults, openRun } from './results.js';
import { renderHistory } from './history.js';
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
  renderHelp(document.getElementById('tab-help'));

  events(async (evt) => {
    jobUi(evt);
    state.emit('event', evt);
    if (evt.type === 'catalog') {
      const n = evt.added.length;
      toast(n + ' new file' + (n > 1 ? 's' : '') + ' found in watched folders: ' + evt.added.map((e) => e.name).slice(0, 3).join(', ')
        + (n > 3 ? '…' : ''), 'ok', 8000);
      await refreshCatalog();
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
