/**
 * Minimal canvas line chart: x values (hours by default), several series, grid, legend and a
 * hover read-out. No dependencies.
 */
export function lineChart(canvas, opts) {
  const state = { hover: null };
  const draw = () => {
    const dpr = window.devicePixelRatio || 1;
    const w = canvas.clientWidth || 500, h = canvas.clientHeight || 220;
    canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
    const g = canvas.getContext('2d');
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    const css = getComputedStyle(document.documentElement);
    const ink = css.getPropertyValue('--ink').trim(), muted = css.getPropertyValue('--muted').trim(), line = css.getPropertyValue('--line').trim();
    g.clearRect(0, 0, w, h);
    const L = 58, R = 12, T = 22, B = 30;
    const xs = opts.x;
    const series = opts.series.filter((s) => s.y && s.y.some((v) => v != null && Number.isFinite(v)));
    g.font = '12px system-ui';
    g.fillStyle = ink;
    g.fillText(opts.title || '', L, 14);
    if (!xs.length || !series.length) { g.fillStyle = muted; g.fillText('no data', L, h / 2); return; }
    let x0 = Math.min(...xs), x1 = Math.max(...xs);
    if (x1 === x0) x1 = x0 + 1;
    const ys = series.flatMap((s) => s.y.filter((v) => v != null && Number.isFinite(v)));
    let y0 = opts.yMin ?? Math.min(...ys), y1 = Math.max(...ys);
    if (y1 === y0) { y1 += 1; y0 -= 1; }
    const pad = (y1 - y0) * 0.06; y1 += pad; if (opts.yMin == null) y0 -= pad;
    const X = (v) => L + (v - x0) / (x1 - x0) * (w - L - R), Y = (v) => T + (1 - (v - y0) / (y1 - y0)) * (h - T - B);
    g.strokeStyle = line; g.fillStyle = muted; g.lineWidth = 1;
    const ticks = (a, b, n) => { const s = Math.pow(10, Math.floor(Math.log10((b - a) / n))); const m = [1, 2, 5, 10].find((k) => (b - a) / (k * s) <= n) * s; const out = []; for (let v = Math.ceil(a / m) * m; v <= b; v += m) out.push(+v.toPrecision(10)); return out; };
    for (const v of ticks(y0, y1, 5)) { g.beginPath(); g.moveTo(L, Y(v)); g.lineTo(w - R, Y(v)); g.stroke(); g.fillText(fmt(v), 4, Y(v) + 4); }
    for (const v of ticks(x0, x1, 8)) { g.beginPath(); g.moveTo(X(v), T); g.lineTo(X(v), h - B); g.stroke(); g.fillText(fmt(v), X(v) - 10, h - B + 14); }
    g.fillText(opts.xLabel || '', w - R - g.measureText(opts.xLabel || '').width, h - 4);
    g.save(); g.translate(12, T + 60); g.restore();
    let lx = L + g.measureText(opts.title || '').width + 18; // legend starts after the title
    for (const s of series) {
      g.strokeStyle = s.color; g.lineWidth = s.width || 1.5; g.setLineDash(s.dash || []);
      g.beginPath();
      let pen = false;
      s.y.forEach((v, k) => {
        if (v == null || !Number.isFinite(v)) { pen = false; return; }
        if (s.points) { g.fillStyle = s.color; g.fillRect(X(xs[k]) - 1.5, Y(v) - 1.5, 3, 3); return; }
        if (!pen) { g.moveTo(X(xs[k]), Y(v)); pen = true; } else g.lineTo(X(xs[k]), Y(v));
      });
      g.stroke(); g.setLineDash([]);
      g.fillStyle = s.color; g.fillRect(lx, 8, 10, 3); g.fillStyle = muted;
      g.fillText(s.name + (opts.yUnit ? ' [' + opts.yUnit + ']' : ''), lx + 14, 14);
      lx += g.measureText(s.name + (opts.yUnit ? ' [' + opts.yUnit + ']' : '')).width + 30;
    }
    if (state.hover != null) {
      const k = state.hover;
      g.strokeStyle = muted; g.beginPath(); g.moveTo(X(xs[k]), T); g.lineTo(X(xs[k]), h - B); g.stroke();
      const txt = fmt(xs[k]) + ' ' + (opts.xLabel || '') + ': ' + series.map((s) => s.name + ' ' + (s.y[k] == null ? '—' : fmt(s.y[k]))).join(', ');
      g.fillStyle = ink; g.fillText(txt, Math.min(X(xs[k]) + 6, w - g.measureText(txt).width - 4), T + 12);
    }
    state.X = X; state.L = L; state.R = R; state.w = w;
  };
  const fmt = (v) => (Math.abs(v) >= 1e5 || (Math.abs(v) < 1e-2 && v !== 0) ? v.toExponential(1) : +v.toFixed(Math.abs(v) < 10 ? 2 : 1));
  canvas.onmousemove = (e) => {
    const r = canvas.getBoundingClientRect();
    const px = e.clientX - r.left;
    let best = null, bd = 1e9;
    opts.x.forEach((v, k) => { const d = Math.abs(state.X(v) - px); if (d < bd) { bd = d; best = k; } });
    state.hover = best; draw();
  };
  canvas.onmouseleave = () => { state.hover = null; draw(); };
  new ResizeObserver(draw).observe(canvas);
  draw();
  return draw;
}
