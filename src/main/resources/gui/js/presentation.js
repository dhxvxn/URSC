import { el } from './ui.js';

const labels = {
  'improved-broadcast': 'Improved Broadcast — nominal',
  'original-four-tier': 'Original four-product comparison',
  'retrospective-old-maneuvers': 'Retrospective — original candidate maneuvers',
  'retrospective-corrected-maneuvers': 'Retrospective — corrected candidate maneuvers',
};
const format = (n) => n == null ? 'Not scored' : Number(n).toLocaleString(undefined, {maximumFractionDigits: 2});
function download(name, text) {
  const url = URL.createObjectURL(new Blob([text], {type: 'text/plain;charset=utf-8'}));
  const a = el('a', {href: url, download: name}); a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Read-only archived experiment viewer: intentionally has no computation API calls. */
export async function renderPresentation(root) {
  root.replaceChildren(el('h2', {}, 'Saved two-month presentation'),
    el('p', {}, 'Loading bundled results — no estimation or propagation will run.'));
  try {
    const response = await fetch('presentation/snapshot.json');
    if (!response.ok) throw new Error('Bundled snapshot unavailable (' + response.status + ')');
    const data = await response.json();
    const group = el('select', {'aria-label': 'Archived experiment'});
    ['improved-broadcast', ...data.groups.filter(g => g !== 'improved-broadcast')].forEach(g => group.append(el('option', {value:g}, labels[g])));
    const tier = el('select', {'aria-label': 'Product tier'}, ...['all','broadcast','ultra-rapid','rapid','final'].map(t => el('option', {value:t}, t)));
    const list = el('div', {style:'overflow:auto;max-height:460px'});
    const details = el('div', {id:'presentation-details', style:'margin-top:20px;overflow:auto'});
    const totals = el('p', {id:'presentation-counts'});
    root.replaceChildren(
      el('h2', {}, 'Saved two-month presentation — Sentinel-3A'),
      el('p', {}, '61 daily cutoffs: 3 September–2 November 2024, 05:59:42 UTC. Seven-day predictions extend to 9 November. All results below are already computed and bundled in this application.'),
      el('p', {class:'notice'}, 'Daily benchmark samples only (8 states per issued forecast). These are not dense orbit tracks. Final/Rapid inputs are retrospective; product publication vintages were not verified. Maneuver replays use future reference-derived events.'),
      el('p', {}, el('a', {href:'presentation/TWO_MONTH_OEM_PACKAGE.zip',download:'S3A_TWO_MONTH_OEM.zip'}, 'Download all OEMs'), ' · ',
        el('a', {href:'presentation/OEM_OUTPUT_REPORT.pdf',download:'OEM_OUTPUT_REPORT.pdf'}, 'Output explanation PDF'), ' · ',
        el('a', {href:'presentation/coverage.csv',download:'coverage.csv'}, 'Complete coverage CSV')),
      el('label', {}, 'Experiment ', group), ' ', el('label', {}, 'Product tier ', tier), totals, list, details);
    function show(c) {
      const score = day => {
        const s=c.scores[String(day)];return s && s.status === 'SCORED' ? format(s.positionErrorM)+' m' : (s?.status || 'Not scored');
      };
      details.replaceChildren(el('h3', {}, labels[c.group]+' / '+c.case),
        el('p', {}, 'Archived status: '+c.status+' · Day 1: '+score(1)+' · Day 7: '+score(7)),
        el('p', {}, c.start_utc ? 'Saved interval: '+c.start_utc+' to '+c.stop_utc+' UTC · ITRF' : 'No issued ephemeris. This rejected case has not been replaced with reference data.'));
      if (c.oem) {
        details.append(el('button', {onclick:()=>download(c.oem_file.split('/').pop(),c.oem)}, 'Download this OEM'));
        const table=el('table', {style:'width:100%;font-variant-numeric:tabular-nums'});
        table.append(el('thead', {}, el('tr', {}, ...['UTC','Day','X (km)','Y (km)','Z (km)','VX (km/s)','VY (km/s)','VZ (km/s)'].map(x=>el('th', {},x)))));
        const body=el('tbody');
        for(const s of c.samples) body.append(el('tr', {},el('td', {},s.epoch_utc),el('td', {},Number(s.elapsed_days)),
          ...['x_itrf_m','y_itrf_m','z_itrf_m','vx_itrf_ms','vy_itrf_ms','vz_itrf_ms'].map(k=>el('td', {},(Number(s[k])/1000).toFixed(6)))));
        table.append(body);details.append(table);
        details.append(el('p', {}, 'Display rounded to 6 decimals; OEM preserves the archived precision. No covariance was saved in these campaign CSVs.'));
      }
      const provenance=el('details', {},el('summary', {}, 'Saved inputs, fit and environment metadata'),el('pre', {style:'white-space:pre-wrap'},JSON.stringify({inputs:c.inputs,fit:c.fit,environment:c.environment,sourceCsvSha256:c.sourceSha256},null,2)));
      details.append(provenance);
    }
    function refresh() {
      const cases=data.cases.filter(c=>c.group===group.value&&(tier.value==='all'||c.tier===tier.value));
      totals.textContent=cases.length+' attempts · '+cases.filter(c=>c.samples.length).length+' issued outputs · '+cases.filter(c=>!c.samples.length).length+' not issued. Select a row to inspect saved samples.';
      const table=el('table', {style:'width:100%;font-variant-numeric:tabular-nums'});
      table.append(el('thead', {},el('tr', {},...['Date / tier','Status','Day 1 error (m)','Day 7 error (m)','Open'].map(t=>el('th', {},t)))));
      const body=el('tbody');
      for(const c of cases) {
        const value=d=>c.scores[String(d)]?.status==='SCORED'?format(c.scores[String(d)].positionErrorM):'Not scored';
        body.append(el('tr', {},el('td', {},c.case),el('td', {},c.status),el('td', {},value(1)),el('td', {},value(7)),el('td', {},el('button', {onclick:()=>show(c)},'View'))));
      }
      table.append(body);list.replaceChildren(table);
      if(cases.length) show(cases[0]);else details.replaceChildren(el('p', {},'No cases in this filter.'));
    }
    group.addEventListener('change',refresh);tier.addEventListener('change',refresh);refresh();
  } catch(e) {root.replaceChildren(el('h2', {},'Saved presentation'),el('p', {},e.message+' Rebuild or use the complete OEM ZIP from GitHub. No run has been started.'));}
}
