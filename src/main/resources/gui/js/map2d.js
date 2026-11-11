/**
 * Interactive equirectangular ground-track map: country borders, day/night shading with a
 * terminator line, the forecast and reference tracks, ground stations, and a sub-solar point.
 * Moving the pointer over it scrubs time across the whole Results view (globe, map and plots);
 * layers can be toggled. Pure canvas, no dependencies.
 */
export class GroundMap {
  constructor(canvas) {
    this.cv = canvas;
    this.layers = { night: true, ref: true, stations: true, fullTrack: true, grid: true };
    this.hoverXY = null;      // last pointer position, for the crosshair
    this.onScrub = null;      // called with a sample index while hovering
    this.onPick = null;       // called with a sample index on click
    new ResizeObserver(() => this.draw()).observe(canvas);
    canvas.style.cursor = 'crosshair';
    canvas.addEventListener('mousemove', (e) => this._pointer(e));
    canvas.addEventListener('mouseleave', () => { this.hoverXY = null; this.draw(); });
    canvas.addEventListener('click', (e) => { const i = this._nearest(e); if (i != null && this.onPick) this.onPick(i); });
  }

  setBorders(lines) { this.borders = lines; this.draw(); }
  setTrajectory(traj, ref) { this.traj = traj; this.ref = ref || null; this.draw(); }
  setTime(f) { this.time = f; this.draw(); }
  setStations(list) { this.stationList = list || []; this.draw(); }
  setLayers(patch) { Object.assign(this.layers, patch); this.draw(); }

  _geom() {
    const w = this.cv.clientWidth || 600, h = this.cv.clientHeight || 300;
    return { w, h, X: (lon) => (lon + 180) / 360 * w, Y: (lat) => (90 - lat) / 180 * h };
  }

  /** Nearest trajectory sample to the pointer, by on-screen distance. */
  _nearest(e) {
    const t = this.traj; if (!t) return null;
    const r = this.cv.getBoundingClientRect();
    const px = e.clientX - r.left, py = e.clientY - r.top;
    const { X, Y } = this._geom();
    let best = null, bd = 1e9;
    for (let k = 0; k < t.lon.length; k++) {
      const dx = X(t.lon[k]) - px, dy = Y(t.lat[k]) - py, d = dx * dx + dy * dy;
      if (d < bd) { bd = d; best = k; }
    }
    return bd < 60 * 60 ? best : null;   // only within ~60 px of the track
  }

  _pointer(e) {
    const r = this.cv.getBoundingClientRect();
    this.hoverXY = [e.clientX - r.left, e.clientY - r.top];
    const i = this._nearest(e);
    if (i != null && this.onScrub) this.onScrub(i);   // scrub the whole view
    else this.draw();
  }

  draw() {
    const cv = this.cv;
    const dpr = window.devicePixelRatio || 1;
    const { w, h, X, Y } = this._geom();
    if (cv.width !== Math.round(w * dpr)) { cv.width = Math.round(w * dpr); cv.height = Math.round(h * dpr); }
    const g = cv.getContext('2d');
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    const d = Math.PI / 180;
    // ocean
    g.fillStyle = '#0d2238'; g.fillRect(0, 0, w, h);
    // graticule
    if (this.layers.grid) {
      g.strokeStyle = 'rgba(80,120,160,.35)'; g.lineWidth = 0.5;
      for (let lon = -180; lon <= 180; lon += 30) { g.beginPath(); g.moveTo(X(lon), 0); g.lineTo(X(lon), h); g.stroke(); }
      for (let lat = -60; lat <= 60; lat += 30) { g.beginPath(); g.moveTo(0, Y(lat)); g.lineTo(w, Y(lat)); g.stroke(); }
    }
    if (this.borders) {
      g.strokeStyle = 'rgba(170,205,240,.8)'; g.lineWidth = 0.6; g.beginPath();
      for (const l of this.borders) {
        g.moveTo(X(l[0]), Y(l[1]));
        for (let i = 2; i < l.length; i += 2) {
          if (Math.abs(l[i] - l[i - 2]) > 180) g.moveTo(X(l[i]), Y(l[i + 1])); else g.lineTo(X(l[i]), Y(l[i + 1]));
        }
      }
      g.stroke();
    }
    const t = this.traj;
    const n = t ? t.lat.length : 0;
    const i = t ? Math.max(0, Math.min(n - 1, Math.floor(this.time || 0))) : 0;
    // day/night from the sub-solar point at the current epoch
    let so = 0, sl = 0;
    if (t && t.sunItrf) {
      const sx = t.sunItrf[3 * i], sy = t.sunItrf[3 * i + 1], sz = t.sunItrf[3 * i + 2];
      const sr = Math.hypot(sx, sy, sz) || 1;
      sl = Math.asin(sz / sr); so = Math.atan2(sy, sx);
      if (this.layers.night) {
        g.fillStyle = 'rgba(0,0,0,.40)';
        for (let lat = -88; lat < 90; lat += 3) for (let lon = -180; lon < 180; lon += 3) {
          const cz = Math.sin(lat * d) * Math.sin(sl) + Math.cos(lat * d) * Math.cos(sl) * Math.cos(lon * d - so);
          if (cz < 0) g.fillRect(X(lon - 1.5), Y(lat + 1.5), w / 120 + 0.6, h / 60 + 0.6);
        }
        // terminator line
        g.strokeStyle = 'rgba(255,214,107,.6)'; g.lineWidth = 1; g.beginPath();
        let pen = false, px = 0;
        for (let lon = -180; lon <= 180; lon += 2) {
          // latitude where the solar elevation is zero for this longitude
          const tanLat = -Math.cos(lon * d - so) / Math.tan(sl || 1e-6);
          const lat = Math.atan(tanLat) / d;
          const x = X(lon), y = Y(lat);
          if (!pen || Math.abs(x - px) > 180) { g.moveTo(x, y); pen = true; } else g.lineTo(x, y);
          px = x;
        }
        g.stroke();
      }
    }
    // stations
    if (this.layers.stations) for (const s of this.stationList || []) {
      g.fillStyle = '#3fbf74'; g.beginPath();
      g.moveTo(X(s.lonDeg), Y(s.latDeg) - 6); g.lineTo(X(s.lonDeg) - 5, Y(s.latDeg) + 4); g.lineTo(X(s.lonDeg) + 5, Y(s.latDeg) + 4); g.fill();
      g.font = '10px system-ui'; g.fillStyle = '#9fe3b8'; g.fillText(s.name || '', X(s.lonDeg) + 7, Y(s.latDeg) + 4);
    }
    if (!t) { this._legend(g, w, h); return; }
    const track = (tr, from, to, color, width) => {
      g.strokeStyle = color; g.lineWidth = width; g.beginPath();
      for (let k = from; k <= to && k < tr.lat.length; k++) {
        if (k === from || Math.abs(tr.lon[k] - tr.lon[k - 1]) > 180) g.moveTo(X(tr.lon[k]), Y(tr.lat[k]));
        else g.lineTo(X(tr.lon[k]), Y(tr.lat[k]));
      }
      g.stroke();
    };
    if (this.ref && this.layers.ref) track(this.ref, 0, this.ref.lat.length - 1, 'rgba(192,139,255,.85)', 1.2);
    if (this.layers.fullTrack) track(t, 0, n - 1, 'rgba(79,156,247,.30)', 1);
    track(t, Math.max(0, i - 90), i, '#7fd3ff', 2.2);                 // recent arc, bright
    // sub-solar point
    if (t.sunItrf) { g.fillStyle = '#ffd86b'; g.beginPath(); g.arc(X(so / d), Y(sl / d), 4, 0, 2 * Math.PI); g.fill(); }
    // current satellite position
    g.fillStyle = '#ffcc33'; g.strokeStyle = '#1a2436'; g.lineWidth = 1.5;
    g.beginPath(); g.arc(X(t.lon[i]), Y(t.lat[i]), 5.5, 0, 2 * Math.PI); g.fill(); g.stroke();
    // hover crosshair
    if (this.hoverXY) {
      g.strokeStyle = 'rgba(230,240,255,.35)'; g.lineWidth = 0.8; g.setLineDash([4, 4]);
      g.beginPath(); g.moveTo(this.hoverXY[0], 0); g.lineTo(this.hoverXY[0], h);
      g.moveTo(0, this.hoverXY[1]); g.lineTo(w, this.hoverXY[1]); g.stroke(); g.setLineDash([]);
    }
    this._legend(g, w, h);
  }

  _legend(g, w, h) {
    const items = [['#7fd3ff', 'forecast'], ['#c08bff', 'reference'], ['#3fbf74', 'station'], ['#ffd86b', 'Sun']];
    g.font = '10px system-ui';
    let x = 8; const y = h - 8;
    g.fillStyle = 'rgba(6,14,26,.72)';
    const total = items.reduce((a, [, l]) => a + 16 + g.measureText(l).width + 10, 6);
    g.fillRect(4, h - 20, total, 16);
    for (const [c, l] of items) {
      g.fillStyle = c; g.fillRect(x, y - 7, 10, 3); g.fillStyle = '#c7d4e6';
      g.fillText(l, x + 14, y - 4); x += 16 + g.measureText(l).width + 10;
    }
  }
}
