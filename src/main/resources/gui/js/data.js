import { api } from './api.js';
import { el, toast, browse, fmtBytes, shortUtc } from './ui.js';
import { state, refreshCatalog } from './app.js';

const COLORS = { GNSS_OBSERVATIONS: '#4f9cf7', GPS_BROADCAST_NAV: '#e0a44a', IGS_ORBIT_SP3: '#3fbf74', IGS_CLOCK: '#2bb3b3',
  POEORB: '#a583f0', ATTITUDE_PROQUA: '#ec6bb0', EOP: '#93a1b3', SPACE_WEATHER: '#f06b52', DE_EPHEMERIS: '#d4bd57',
  GRAVITY_FIELD: '#6f9bf0', OEM: '#cfe0f5', UNKNOWN: '#555' };

export function renderData(root) {
  const filterKind = el('select', {}, el('option', { value: '' }, 'All kinds'));
  const filterText = el('input', { type: 'search', placeholder: 'Filter by name…' });
  const tbody = el('tbody');
  const watched = el('div', { class: 'row' });
  const timeline = el('canvas', { class: 'timeline' });
  const summary = el('div', { class: 'muted small' });

  const add = async (mode) => {
    const paths = await browse(mode);
    if (!paths) return;
    try {
      if (mode === 'folder') {
        await api('catalog/watch', { method: 'POST', body: { folder: paths[0] } });
        toast('Watching ' + paths[0] + ' (new files are added automatically)', 'ok');
      } else {
        const r = await api('catalog/add', { method: 'POST', body: { paths } });
        toast(r.added.length + ' file(s) added or updated', 'ok');
      }
      await refreshCatalog();
    } catch (e) { toast(e.message, 'bad'); }
  };

  root.append(
    el('h2', {}, 'Data catalog'),
    el('div', { class: 'card' },
      el('div', { class: 'row' },
        el('button', { class: 'btn primary', onclick: () => add('multi') }, '+ Add files / folders'),
        el('button', { class: 'btn', onclick: () => add('folder') }, '👁 Watch folder'),
        el('button', { class: 'btn', onclick: async () => { const r = await api('catalog/rescan', { method: 'POST' }); toast(r.changed.length + ' changed'); refreshCatalog(); } }, '↻ Rescan'),
        el('button', { class: 'btn', title: 'Write a clearly labelled SIMULATED dataset to try the full workflow', onclick: async () => {
          const r = await browse('folder');
          if (!r) return;
          try { const d = await api('demo', { method: 'POST', body: { folder: r[0] } }); toast('Creating simulated demo data in ' + d.folder + ' …'); } catch (e) { toast(e.message, 'bad'); }
        } }, '🧪 Create demo dataset'),
        el('span', { class: 'muted small' }, 'Files are recognised by their content. Watched folders pick up new files automatically.')),
      el('div', { style: 'margin-top:10px' }, el('b', { class: 'small' }, 'Watched folders: '), watched)),
    el('div', { class: 'card' }, el('h3', {}, 'Coverage'), timeline, summary),
    el('div', { class: 'card' },
      el('div', { class: 'row', style: 'margin-bottom:8px' }, filterKind, filterText),
      el('div', { class: 'table-wrap' }, el('table', {},
        el('thead', {}, el('tr', {}, ...['Kind', 'Name', 'Sat', 'Class', 'Coverage (UTC)', 'Size', 'Note', ''].map((h) => el('th', {}, h)))),
        tbody))));

  const draw = () => {
    const cat = state.catalog;
    if (filterKind.options.length === 1) {
      cat.kinds.forEach((k) => filterKind.append(el('option', { value: k.name }, k.label)));
    }
    watched.replaceChildren(...(cat.watched.length ? cat.watched.map((w) => el('span', { class: 'chip' }, w,
      el('button', { title: 'Stop watching', onclick: async () => { await api('catalog/unwatch', { method: 'POST', body: { folder: w } }); refreshCatalog(); } }, '✕')))
      : [el('span', { class: 'muted small' }, 'none')]));
    const kindLabel = Object.fromEntries(cat.kinds.map((k) => [k.name, k.label]));
    const q = filterText.value.toLowerCase();
    const rows = cat.entries.filter((e) => (!filterKind.value || e.kind === filterKind.value) && (!q || e.name.toLowerCase().includes(q)));
    tbody.replaceChildren(...rows.map((e) => el('tr', {},
      el('td', {}, el('span', { class: 'badge', style: 'color:' + COLORS[e.kind] }, kindLabel[e.kind] || e.kind)),
      el('td', { class: 'path', title: e.path + '\nSHA-256 ' + e.sha256 }, e.name),
      el('td', {}, e.satellite), el('td', {}, e.productClass),
      el('td', { class: 'small' }, e.coverageStart ? shortUtc(e.coverageStart) + ' → ' + shortUtc(e.coverageEnd) : ''),
      el('td', { class: 'small' }, fmtBytes(e.sizeBytes)), el('td', { class: 'small muted' }, e.note),
      el('td', {}, el('button', { class: 'btn small', title: 'Forget (the file is not deleted)', onclick: async () => { await api('catalog/remove', { method: 'POST', body: { path: e.path } }); refreshCatalog(); } }, '✕')))));
    summary.textContent = cat.entries.length + ' files catalogued';
    drawTimeline(timeline, cat.entries, kindLabel);
  };
  filterKind.addEventListener('change', draw);
  filterText.addEventListener('input', draw);
  state.on('catalog', draw);
  state.on('tab', (t) => { if (t === 'data') draw(); });
  draw();
}

function drawTimeline(cv, entries, kindLabel) {
  const withCov = entries.filter((e) => e.coverageStart);
  const dpr = window.devicePixelRatio || 1;
  const w = cv.clientWidth || 800, h = cv.clientHeight || 200;
  cv.width = w * dpr; cv.height = h * dpr;
  const g = cv.getContext('2d');
  g.scale(dpr, dpr);
  g.clearRect(0, 0, w, h);
  const css = getComputedStyle(document.documentElement);
  g.fillStyle = css.getPropertyValue('--muted');
  g.font = '12px system-ui';
  if (!withCov.length) { g.fillText('No files with known coverage yet. Add data to see what dates are covered.', 10, 24); return; }
  const t = (s) => Date.parse(s.replace(/\.\d+$/, '') + 'Z');
  let lo = Math.min(...withCov.map((e) => t(e.coverageStart))), hi = Math.max(...withCov.map((e) => t(e.coverageEnd)));
  if (hi - lo < 86400e3) hi = lo + 86400e3;
  const kinds = [...new Set(withCov.map((e) => e.kind))];
  const left = 190, right = w - 10, rowH = Math.min(26, (h - 30) / kinds.length);
  const x = (v) => left + (v - lo) / (hi - lo) * (right - left);
  kinds.forEach((k, i) => {
    const y = 6 + i * rowH;
    g.fillStyle = css.getPropertyValue('--muted');
    g.fillText(kindLabel[k] ? kindLabel[k].replace(/\s*\(.*\)/, '') : k, 6, y + rowH * 0.65);
    g.fillStyle = COLORS[k];
    withCov.filter((e) => e.kind === k).forEach((e) => {
      g.globalAlpha = 0.75;
      g.fillRect(x(t(e.coverageStart)), y + 3, Math.max(2, x(t(e.coverageEnd)) - x(t(e.coverageStart))), rowH - 6);
    });
    g.globalAlpha = 1;
  });
  g.fillStyle = css.getPropertyValue('--muted');
  const days = (hi - lo) / 86400e3;
  const step = Math.max(1, Math.ceil(days / 10)) * 86400e3;
  for (let v = Math.ceil(lo / 86400e3) * 86400e3; v <= hi; v += step) {
    const d = new Date(v).toISOString().slice(0, 10);
    g.fillText(d, x(v) - 30, h - 6);
    g.fillRect(x(v), h - 22, 1, 6);
  }
}
