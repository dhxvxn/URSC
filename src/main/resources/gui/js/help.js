import { api } from './api.js';
import { el, markdown, toast } from './ui.js';

const PAGES = [['getting-started', 'Getting started'], ['inputs', 'Input data'], ['product-priority', 'GPS product priority'],
  ['perturbations', 'Perturbations & models'], ['outputs', 'Outputs (OEM, covariance)'], ['offline', 'Offline operation & AI'],
  ['troubleshooting', 'Troubleshooting']];

const q = el('input', { type: 'search', placeholder: 'Ask a question or search, e.g. "which density model should I use?"', style: 'flex:1' });
const results = el('div');
const page = el('div', { class: 'card' });
const providers = el('div', { class: 'small muted' });

export function renderHelp(root) {
  const form = el('form', { class: 'row', onsubmit: (e) => { e.preventDefault(); search(q.value); } }, q, el('button', { class: 'btn primary' }, 'Search'));
  root.append(el('h2', {}, 'Help'),
    el('div', { class: 'card' }, form, providers, results),
    el('div', { class: 'helpgrid' },
      el('div', { class: 'card helpnav' }, el('b', {}, 'Guides'),
        el('a', { class: 'btn primary', href: 'guide/', target: '_blank', rel: 'noopener', style: 'text-align:center;text-decoration:none' }, 'Interactive feature guide ↗'),
        ...PAGES.map(([f, t]) => el('button', { onclick: () => show(f) }, t))),
      page));
  show('getting-started');
  api('status').then((s) => {
    const sv = s.search;
    providers.replaceChildren('Searches ' + sv.passages + ' passages from ' + sv.documents + ' documents (help, every setting, project docs, and your notes in ' + sv.knowledgeFolder + '). Answer providers in priority order: ',
      ...sv.providers.map((p, i) => el('span', {}, (i ? ' → ' : '') + p.name + ' (' + p.status + ')')));
  }).catch(() => {});
}

async function show(name) {
  const t = await (await fetch('help/' + name + '.md')).text();
  page.replaceChildren(markdown(t));
}

export async function search(text) {
  q.value = text;
  if (!text.trim()) return;
  let r;
  try { r = await api('search?q=' + encodeURIComponent(text) + '&n=8'); } catch (e) { toast(e.message, 'bad'); return; }
  const items = r.results.map((p, i) => el('div', { class: 'passage' },
    el('div', { class: 'src' }, '[' + (i + 1) + '] ' + p.source + ' — ' + p.title),
    el('div', { class: 'small' }, p.text.length > 600 ? p.text.slice(0, 600) + ' …' : p.text)));
  results.replaceChildren(
    r.answer ? el('div', { class: 'answer', style: 'margin-top:10px' }, el('div', { class: 'small muted' }, 'Answer (' + r.answer.provider + ', from the passages below)'), r.answer.text) : null,
    items.length ? el('div', {}, ...items) : el('p', { class: 'muted' }, 'Nothing found. Try other words.'));
}
