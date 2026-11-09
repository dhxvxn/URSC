import { api, apiUrl } from './api.js';
import { el, toast, browse, infoPopover, modal, shortUtc } from './ui.js';
import { state, refreshCatalog } from './app.js';

const clone = (o) => JSON.parse(JSON.stringify(o));
const get = (o, path) => path.split('.').reduce((a, k) => (a == null ? a : a[k]), o);
function set(o, path, v) {
  const ks = path.split('.');
  let cur = o;
  ks.slice(0, -1).forEach((k) => { cur = cur[k] ??= {}; });
  cur[ks[ks.length - 1]] = v;
}

let cfg = null;

export function currentConfig() { return cfg; }

export function loadConfig(c) {
  cfg = Object.assign(clone(state.defaults), clone(c));
  state.emit('config', cfg);
}

export function renderRun(root) {
  cfg = clone(state.defaults);
  const errorsBox = el('ul', { class: 'errors' });
  const readyLine = el('div', { class: 'okline hidden' }, '✓ Ready to run');
  const runBtn = el('button', { class: 'btn primary', disabled: true }, '▶ Run');
  const cancelBtn = el('button', { class: 'btn danger hidden' }, '■ Cancel');
  const progress = el('div', { class: 'hidden' }, el('div', { class: 'job-bar', style: 'width:100%;height:8px' }, el('div')),
    el('div', { class: 'small muted', style: 'margin-top:4px' }), el('div', { class: 'log', style: 'margin-top:8px' }));
  const presetSel = el('select', {});
  const groupsBox = el('div');
  const wfToggle = el('div', { class: 'toggle' });

  const refreshPresets = async () => {
    const r = await api('presets');
    presetSel.replaceChildren(el('option', { value: '' }, '— presets —'), ...r.presets.map((p) => el('option', { value: p }, p)));
  };

  root.append(el('h2', {}, 'Run setup'),
    el('div', { class: 'card' },
      el('div', { class: 'row' }, el('b', {}, 'Workflow'), wfToggle,
        el('span', { style: 'flex:1' }),
        presetSel,
        el('button', { class: 'btn', onclick: async () => { if (!presetSel.value) return; loadConfig(await api('presets/' + encodeURIComponent(presetSel.value))); toast('Preset loaded'); } }, 'Load'),
        el('button', { class: 'btn', onclick: savePreset }, 'Save as…'),
        el('button', { class: 'btn', onclick: async () => { if (!presetSel.value || !confirm('Delete preset ' + presetSel.value + '?')) return; await api('presets/' + encodeURIComponent(presetSel.value), { method: 'DELETE' }); refreshPresets(); } }, 'Delete'),
        el('button', { class: 'btn', onclick: exportCfg }, 'Export'),
        el('button', { class: 'btn', onclick: importCfg }, 'Import'),
        el('button', { class: 'btn', title: 'Reset every setting to its default', onclick: () => { if (confirm('Reset all settings to defaults?')) loadConfig({}); } }, 'Reset'))),
    groupsBox,
    el('div', { class: 'card' },
      el('div', { class: 'row' }, runBtn, cancelBtn, readyLine), errorsBox, progress));

  async function savePreset() {
    const name = prompt('Preset name', cfg.runName || '');
    if (!name) return;
    await api('presets', { method: 'POST', body: { name, config: cfg } });
    toast('Preset saved');
    refreshPresets();
  }
  function exportCfg() {
    const a = el('a', { href: URL.createObjectURL(new Blob([JSON.stringify(cfg, null, 2)], { type: 'application/json' })), download: (cfg.runName || 'ursc-config') + '.json' });
    a.click();
  }
  function importCfg() {
    const inp = el('input', { type: 'file', accept: '.json,application/json' });
    inp.addEventListener('change', async () => {
      try { loadConfig(JSON.parse(await inp.files[0].text())); toast('Settings imported'); } catch (e) { toast('Not a valid settings file: ' + e.message, 'bad'); }
    });
    inp.click();
  }

  let validateTimer = null;
  const validate = () => {
    clearTimeout(validateTimer);
    validateTimer = setTimeout(async () => {
      try {
        const r = await api('config/validate', { method: 'POST', body: cfg });
        errorsBox.replaceChildren(...r.errors.map((e) => el('li', {}, e)));
        readyLine.classList.toggle('hidden', r.errors.length > 0);
        runBtn.disabled = r.errors.length > 0 || !!state.job;
      } catch (e) { errorsBox.replaceChildren(el('li', {}, e.message)); }
    }, 350);
  };

  const changed = () => { draw(); validate(); };
  // settings whose value changes which other settings are shown
  const structural = new Set(['workflow', ...state.params.params.filter((p) => p.showIf).map((p) => p.showIf[0])]);
  const catalogSelects = [];
  const catalogOptions = (p) => state.catalog.entries.filter((e) => !p.kinds || p.kinds.includes(e.kind)).map((e) =>
    el('option', { value: e.path }, e.name + (e.coverageStart ? '  [' + shortUtc(e.coverageStart).slice(0, 10) + ']' : '')));
  const refreshCatalogSelects = () => {
    for (const [sel, p] of catalogSelects) {
      if (!sel.isConnected) continue;
      sel.replaceChildren(el('option', { value: '' }, 'Add from catalog…'), ...catalogOptions(p));
    }
  };

  function field(p) {
    const val = get(cfg, p.key);
    const def = get(state.defaults, p.key);
    const modified = JSON.stringify(val) !== JSON.stringify(def);
    const info = el('button', { class: 'info', type: 'button', title: 'What is this?', onclick: (e) => infoPopover(e.currentTarget, p, def) }, 'i');
    const label = el('label', {}, p.label, p.unit ? el('span', { class: 'unit' }, '[' + p.unit + ']') : null, info,
      modified && !p.mandatory ? el('span', { class: 'mod' }, '● changed') : null,
      modified && !p.mandatory ? el('button', { class: 'reset', type: 'button', onclick: () => { set(cfg, p.key, clone(def)); changed(); } }, 'reset') : null);
    let input;
    const setv = (v) => {
      set(cfg, p.key, v);
      if (structural.has(p.key) || p.type === 'files' || p.type === 'file') { changed(); return; }
      // re-render just this field (keeps the rest of the form, and what the user is doing, intact)
      const old = wrap;
      wrap = field(p);
      old.replaceWith(wrap);
      validate();
    };
    switch (p.type) {
      case 'bool':
        input = el('label', { class: 'row', style: 'gap:6px' }, el('input', { type: 'checkbox', checked: !!val, onchange: (e) => setv(e.target.checked) }), val ? 'on' : 'off');
        break;
      case 'enum':
        input = el('select', { onchange: (e) => setv(e.target.value) }, ...p.options.map(([v, l]) => el('option', { value: v, selected: v === val }, l)));
        break;
      case 'int':
      case 'number':
        input = el('input', { type: 'number', step: p.type === 'int' ? 1 : 'any', min: p.min, max: p.max, value: val,
          onchange: (e) => { const n = p.type === 'int' ? parseInt(e.target.value, 10) : parseFloat(e.target.value); if (!Number.isNaN(n)) setv(n); } });
        break;
      case 'datetime':
        input = el('input', { type: 'datetime-local', step: 1, value: (val || '').replace(/Z$/, '').slice(0, 19),
          onchange: (e) => setv(e.target.value) });
        break;
      case 'files':
      case 'file':
        input = fileField(p, val, setv);
        break;
      default:
        input = el('input', { type: 'text', value: val ?? '', onchange: (e) => setv(e.target.value) });
    }
    let wrap = el('div', { class: 'field' + (p.mandatory ? ' mandatory' : '') + (p.type === 'files' || p.type === 'file' ? ' wide' : '') }, label, input);
    return wrap;
  }

  function fileField(p, val, setv) {
    const multi = p.type === 'files';
    const list = multi ? (val || []) : (val ? [val] : []);
    const items = el('div', { class: 'filelist' }, ...list.map((f) => el('div', { class: 'item' }, el('span', {}, f),
      el('button', { type: 'button', title: 'Remove', onclick: () => setv(multi ? list.filter((x) => x !== f) : '') }, '✕'))));
    const pickFromCatalog = el('select', {}, el('option', { value: '' }, 'Add from catalog…'), ...catalogOptions(p));
    catalogSelects.push([pickFromCatalog, p]);
    pickFromCatalog.addEventListener('change', () => {
      const v = pickFromCatalog.value;
      if (v) setv(multi ? [...new Set([...list, v])] : v);
    });
    const br = el('button', { class: 'btn', type: 'button', onclick: async () => {
      const r = await browse(multi ? 'multi' : 'file');
      if (r) setv(multi ? [...new Set([...list, ...r])] : r[0]);
    } }, 'Browse…');
    return el('div', {}, items, el('div', { class: 'row', style: 'margin-top:4px' }, pickFromCatalog, br,
      list.length === 0 ? el('span', { class: 'muted small' }, multi ? 'none selected' : 'not set (default used)') : null));
  }

  const visible = (p) => {
    if (p.workflows && !p.workflows.includes(cfg.workflow)) return false;
    if (p.showIf) {
      const [k, v] = p.showIf;
      const cur = get(cfg, k);
      if (typeof v === 'string' && v.startsWith('!')) return cur !== v.slice(1);
      return cur === v;
    }
    return true;
  };

  const openState = {};
  function draw() {
    catalogSelects.length = 0;
    wfToggle.replaceChildren(...[['OD_FORECAST', 'OD + Forecast'], ['PROPAGATE', 'Propagate from a state']].map(([v, l]) =>
      el('button', { class: cfg.workflow === v ? 'on' : '', onclick: () => { cfg.workflow = v; changed(); } }, l)));
    groupsBox.replaceChildren();
    for (const g of state.params.groups) {
      const ps = state.params.params.filter((p) => p.group === g && p.key !== 'workflow' && visible(p));
      if (!ps.length) continue;
      const nChanged = ps.filter((p) => !p.mandatory && JSON.stringify(get(cfg, p.key)) !== JSON.stringify(get(state.defaults, p.key))).length;
      const first = g === 'Mandatory inputs' || g === 'Initial state';
      const det = el('details', { class: 'group', open: openState[g] ?? first },
        el('summary', {}, g, el('span', { class: 'count' }, ps.length + ' settings' + (nChanged ? ' · ' + nChanged + ' changed' : ''))),
        el('div', { class: 'fields' }, ...ps.map(field)));
      det.addEventListener('toggle', () => { openState[g] = det.open; });
      if (g === 'Mandatory inputs' && cfg.workflow === 'OD_FORECAST') {
        const satSel = lastSats.length > 1 ? el('select', { title: 'Spacecraft whose observations are used', onchange: (e) => autoSelect(e.target.value) },
          ...lastSats.map((x) => el('option', { value: x, selected: x === lastSat }, 'Spacecraft: ' + x))) : null;
        det.querySelector('.fields').prepend(el('div', { class: 'field wide' }, el('div', { class: 'row' },
          el('button', { class: 'btn', type: 'button', onclick: () => autoSelect(null) }, '✨ Auto-select from catalog'), satSel,
          el('span', { class: 'muted small' }, 'Fills observations (one spacecraft), GPS products, POEORB, attitude, EOP, weather and DE files that cover the cutoff.'))));
      }
      groupsBox.append(det);
    }
  }

  let lastSats = [], lastSat = null;
  async function autoSelect(satellite) {
    if (!cfg.cutoffUtc) { toast('Set the UTC cutoff first', 'warn'); return; }
    try {
      await refreshCatalog();
      const s = await api('catalog/suggest', { method: 'POST', body: { cutoffUtc: cfg.cutoffUtc, arcHours: cfg.od.arcHours, spanDays: cfg.forecast.spanDays, mode: cfg.products.mode, satellite } });
      lastSats = s.satellites; lastSat = s.satellite[0] || null;
      if (lastSats.length > 1) toast('Observations of ' + lastSat + ' selected; also available for this cutoff: ' + lastSats.filter((x) => x !== lastSat).join(', ') + ' (switch with the Spacecraft selector)', 'warn', 9000);
      for (const k of ['gnssrd', 'gpsProducts', 'poeorb', 'proqua']) cfg.inputs[k] = s[k];
      for (const k of ['eopFile', 'spaceWeatherFile', 'de440File', 'gravityFile']) if (!cfg.inputs[k] && s[k].length) cfg.inputs[k] = s[k][0];
      if (s.de440File.length && cfg.forces.thirdBodyEphemeris === 'ANALYTIC') cfg.forces.thirdBodyEphemeris = 'DE440';
      const n = s.gnssrd.length + s.gpsProducts.length;
      toast(n ? 'Selected ' + s.gnssrd.length + ' observation and ' + s.gpsProducts.length + ' GPS product file(s)' : 'No catalogued data covers this cutoff', n ? 'ok' : 'warn');
      changed();
    } catch (e) { toast(e.message, 'bad'); }
  }

  runBtn.addEventListener('click', async () => {
    try {
      const r = await api('run', { method: 'POST', body: cfg });
      state.job = r.id;
      runBtn.disabled = true;
      cancelBtn.classList.remove('hidden');
      progress.classList.remove('hidden');
      progress.querySelector('.log').textContent = '';
      toast('Run ' + r.id + ' started');
    } catch (e) {
      toast(e.message, 'bad', 9000);
    }
  });
  cancelBtn.addEventListener('click', () => state.job && api('runs/' + state.job + '/cancel', { method: 'POST' }));

  state.on('event', (evt) => {
    if (evt.id !== state.job) return;
    if (evt.type === 'progress') {
      progress.querySelector('.job-bar div').style.width = Math.round(evt.fraction * 100) + '%';
      progress.querySelector('.small').textContent = evt.stage;
    } else if (evt.type === 'log') {
      progress.querySelector('.log').textContent += evt.message + '\n';
    } else if (evt.type === 'done') {
      state.job = null;
      cancelBtn.classList.add('hidden');
      progress.querySelector('.small').textContent = 'Finished: ' + evt.status;
      validate();
    }
  });
  state.on('config', changed);
  state.on('demo', (evt) => { if (!cfg.cutoffUtc) { cfg.cutoffUtc = evt.cutoffUtc; changed(); } });
  state.on('catalog', refreshCatalogSelects);
  refreshPresets();
  changed();
}
