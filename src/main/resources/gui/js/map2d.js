/** Flat (equirectangular) ground-track map with country borders and day/night shading. */
export class GroundMap {
  constructor(canvas) {
    this.cv = canvas;
    new ResizeObserver(() => this.draw()).observe(canvas);
  }

  setBorders(lines) { this.borders = lines; this.draw(); }

  setTrajectory(traj, ref) { this.traj = traj; this.ref = ref || null; this.draw(); }

  setTime(f) { this.time = f; this.draw(); }

  draw() {
    const cv = this.cv;
    const dpr = window.devicePixelRatio || 1;
    const w = cv.clientWidth || 600, h = cv.clientHeight || 300;
    if (cv.width !== Math.round(w * dpr)) { cv.width = Math.round(w * dpr); cv.height = Math.round(h * dpr); }
    const g = cv.getContext('2d');
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    const X = (lon) => (lon + 180) / 360 * w, Y = (lat) => (90 - lat) / 180 * h;
    g.fillStyle = '#0d2238'; g.fillRect(0, 0, w, h);
    g.strokeStyle = 'rgba(80,120,160,.35)'; g.lineWidth = 0.5;
    for (let lon = -180; lon <= 180; lon += 30) { g.beginPath(); g.moveTo(X(lon), 0); g.lineTo(X(lon), h); g.stroke(); }
    for (let lat = -60; lat <= 60; lat += 30) { g.beginPath(); g.moveTo(0, Y(lat)); g.lineTo(w, Y(lat)); g.stroke(); }
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
    if (!t) return;
    const n = t.lat.length;
    const f = Math.max(0, Math.min(n - 1, this.time || 0));
    const i = Math.floor(f);
    // night shading from the sub-solar point
    const sx = t.sunItrf[3 * i], sy = t.sunItrf[3 * i + 1], sz = t.sunItrf[3 * i + 2];
    const sl = Math.asin(sz), so = Math.atan2(sy, sx), d = Math.PI / 180;
    g.fillStyle = 'rgba(0,0,0,.42)';
    for (let lat = -88; lat < 90; lat += 4) for (let lon = -178; lon < 180; lon += 4) {
      const cz = Math.sin(lat * d) * Math.sin(sl) + Math.cos(lat * d) * Math.cos(sl) * Math.cos(lon * d - so);
      if (cz < 0) g.fillRect(X(lon - 2), Y(lat + 2), w / 90 + 0.5, h / 45 + 0.5);
    }
    const track = (tr, from, to, color, width) => {
      g.strokeStyle = color; g.lineWidth = width; g.beginPath();
      for (let k = from; k <= to && k < tr.lat.length; k++) {
        if (k === from || Math.abs(tr.lon[k] - tr.lon[k - 1]) > 180) g.moveTo(X(tr.lon[k]), Y(tr.lat[k]));
        else g.lineTo(X(tr.lon[k]), Y(tr.lat[k]));
      }
      g.stroke();
    };
    if (this.ref) track(this.ref, 0, this.ref.lat.length - 1, 'rgba(192,139,255,.8)', 1);
    track(t, 0, n - 1, 'rgba(79,156,247,.35)', 1);
    track(t, Math.max(0, i - 90), i, '#7fd3ff', 2);
    g.fillStyle = '#ffcc33';
    g.beginPath(); g.arc(X(t.lon[i]), Y(t.lat[i]), 5, 0, 2 * Math.PI); g.fill();
    g.fillStyle = '#ffd86b'; g.beginPath(); g.arc(X(so / d), Y(sl / d), 4, 0, 2 * Math.PI); g.fill();
  }
}
