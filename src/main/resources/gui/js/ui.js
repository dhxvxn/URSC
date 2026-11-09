import { api } from './api.js';

export function el(tag, attrs = {}, ...children) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === undefined || v === null || v === false) continue;
    if (k === 'class') e.className = v;
    else if (k === 'text') e.textContent = v;
    else if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
    else if (k === 'style') e.style.cssText = v;
    else e.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) {
    if (c === null || c === undefined || c === false) continue;
    e.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return e;
}

export function toast(msg, kind = '', ms = 5000) {
  const t = el('div', { class: 'toast ' + kind }, msg);
  document.getElementById('toasts').append(t);
  setTimeout(() => t.remove(), ms);
}

export function fmtBytes(n) {
  if (n == null) return '';
  const u = ['B', 'kB', 'MB', 'GB'];
  let i = 0;
  while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
  return (i ? n.toFixed(1) : n) + ' ' + u[i];
}

export const shortUtc = (s) => (s || '').replace('T', ' ').replace(/\.\d+$/, '');

/** Modal dialog; resolves with the value given to close(). */
export function modal(title, body, buttons) {
  return new Promise((resolve) => {
    const back = el('div', { class: 'modal-back' });
    const close = (v) => { back.remove(); document.removeEventListener('keydown', esc); resolve(v); };
    const esc = (e) => { if (e.key === 'Escape') close(null); };
    document.addEventListener('keydown', esc);
    const foot = el('footer', {}, ...buttons.map((b) =>
      el('button', { class: 'btn ' + (b.primary ? 'primary' : ''), onclick: () => close(b.value ? b.value() : b.result) }, b.label)));
    back.append(el('div', { class: 'modal', role: 'dialog' }, el('header', {}, title), el('div', { class: 'body' }, body), foot));
    back.addEventListener('click', (e) => { if (e.target === back) close(null); });
    document.body.append(back);
  });
}

/**
 * Server-side file browser (browsers cannot reveal local paths, the local server can).
 * mode: 'multi' (files and folders), 'file' (one file), 'folder' (one folder).
 */
export async function browse(mode = 'multi', start = '') {
  const chosen = new Set();
  const pathInput = el('input', { type: 'text' });
  const list = el('div', { class: 'fb-list' });
  const roots = el('select', {});
  let current = '';
  const load = async (p) => {
    let d;
    try { d = await api('fs?path=' + encodeURIComponent(p || '')); } catch (e) { toast(e.message, 'bad'); return; }
    current = d.path;
    pathInput.value = d.path;
    roots.replaceChildren(...d.roots.map((r) => el('option', { value: r }, r)), el('option', { value: d.home, selected: true }, 'Home'));
    list.replaceChildren();
    if (d.parent) list.append(el('div', { class: 'fb-item dir' }, el('span', { class: 'name', onclick: () => load(d.parent) }, '⬑ ..')));
    for (const it of d.items) {
      const cb = (mode === 'multi' || (mode === 'file' && !it.dir) || (mode === 'folder' && it.dir))
        ? el('input', { type: mode === 'multi' ? 'checkbox' : 'radio', name: 'fbsel', checked: chosen.has(it.path),
            onchange: (e) => { if (mode !== 'multi') chosen.clear(); e.target.checked ? chosen.add(it.path) : chosen.delete(it.path); } })
        : el('span', { style: 'width:13px' });
      list.append(el('div', { class: 'fb-item' + (it.dir ? ' dir' : '') }, cb,
        el('span', { class: 'name', onclick: it.dir ? () => load(it.path) : null }, (it.dir ? '📁 ' : '') + it.name),
        el('span', { class: 'muted small' }, it.dir ? '' : fmtBytes(it.size))));
    }
  };
  pathInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') load(pathInput.value); });
  roots.addEventListener('change', () => load(roots.value));
  const hint = mode === 'multi' ? 'Tick any files and folders (folders are added with all their contents).'
    : mode === 'folder' ? 'Pick a folder (or open one and press "Use this folder").' : 'Pick one file.';
  const body = el('div', {}, el('div', { class: 'fb-path' }, roots, pathInput,
      el('button', { class: 'btn', onclick: () => load(pathInput.value) }, 'Go')),
    el('div', { class: 'muted small', style: 'margin-bottom:6px' }, hint), list);
  await load(start);
  const buttons = [{ label: 'Cancel', result: null }];
  if (mode === 'folder') buttons.push({ label: 'Use this folder', value: () => [current] });
  buttons.push({ label: 'Select', primary: true, value: () => [...chosen] });
  const r = await modal(mode === 'folder' ? 'Choose a folder' : mode === 'file' ? 'Choose a file' : 'Choose files and folders', body, buttons);
  return r && r.length ? r : null;
}

let openPop = null;
export function infoPopover(anchor, p, defVal) {
  if (openPop) { openPop.remove(); openPop = null; }
  const pop = el('div', { class: 'popover', role: 'tooltip' },
    el('h4', {}, p.label + (p.unit ? ' (' + p.unit + ')' : '')),
    el('div', {}, p.what),
    p.how ? el('div', { class: 'how' }, el('b', {}, 'How to choose: '), p.how) : null,
    el('div', { class: 'meta' }, p.key + (defVal !== undefined ? '  ·  default ' + JSON.stringify(defVal) : '')));
  document.body.append(pop);
  const r = anchor.getBoundingClientRect();
  const left = Math.min(r.left, window.innerWidth - 400);
  pop.style.left = Math.max(8, left) + 'px';
  pop.style.top = (r.bottom + 6 + pop.offsetHeight > window.innerHeight ? r.top - pop.offsetHeight - 6 : r.bottom + 6) + 'px';
  openPop = pop;
  const off = (e) => { if (!pop.contains(e.target) && e.target !== anchor) { pop.remove(); openPop = null; document.removeEventListener('mousedown', off); } };
  setTimeout(() => document.addEventListener('mousedown', off), 0);
}

/** Minimal, safe Markdown renderer (escapes HTML first): headings, lists, tables, code, bold. */
export function markdown(src) {
  const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const inline = (s) => esc(s).replace(/`([^`]+)`/g, '<code>$1</code>').replace(/\*\*([^*]+)\*\*/g, '<b>$1</b>')
    .replace(/\*([^*]+)\*/g, '<i>$1</i>');
  const out = [];
  const lines = src.split('\n');
  let i = 0;
  while (i < lines.length) {
    const l = lines[i];
    if (/^#{1,4} /.test(l)) { const n = l.match(/^#+/)[0].length; out.push(`<h${n + 1}>${inline(l.replace(/^#+\s*/, ''))}</h${n + 1}>`); i++; }
    else if (/^\s*[-*] /.test(l)) { const items = []; while (i < lines.length && /^\s*[-*] /.test(lines[i])) items.push('<li>' + inline(lines[i++].replace(/^\s*[-*] /, '')) + '</li>'); out.push('<ul>' + items.join('') + '</ul>'); }
    else if (/^\s*\d+\. /.test(l)) { const items = []; while (i < lines.length && /^\s*\d+\. /.test(lines[i])) items.push('<li>' + inline(lines[i++].replace(/^\s*\d+\. /, '')) + '</li>'); out.push('<ol>' + items.join('') + '</ol>'); }
    else if (/^\|/.test(l)) {
      const rows = []; while (i < lines.length && /^\|/.test(lines[i])) rows.push(lines[i++]);
      const cells = (r) => r.replace(/^\||\|$/g, '').split('|').map((c) => c.trim());
      const body = rows.filter((r) => !/^\|[\s:|-]+\|$/.test(r));
      out.push('<table><thead><tr>' + cells(body[0]).map((c) => '<th>' + inline(c) + '</th>').join('') + '</tr></thead><tbody>' +
        body.slice(1).map((r) => '<tr>' + cells(r).map((c) => '<td>' + inline(c) + '</td>').join('') + '</tr>').join('') + '</tbody></table>');
    }
    else if (/^```/.test(l)) { const code = []; i++; while (i < lines.length && !/^```/.test(lines[i])) code.push(esc(lines[i++])); i++; out.push('<pre class="log">' + code.join('\n') + '</pre>'); }
    else if (l.trim() === '') { i++; }
    else { const p = []; while (i < lines.length && lines[i].trim() !== '' && !/^(#|\||\s*[-*] |\s*\d+\. |```)/.test(lines[i])) p.push(lines[i++]); out.push('<p>' + inline(p.join(' ')) + '</p>'); }
  }
  const div = el('div', { class: 'md' });
  div.innerHTML = out.join('\n');
  return div;
}
