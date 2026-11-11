import * as THREE from '../vendor/three.module.min.js';
import { MODELS } from './spacecraft.js';

const RE = 6378.137; // km, globe unit
// ECEF (x, y, z-north) -> three.js (y-up, right-handed)
const v3 = (x, y, z) => new THREE.Vector3(x, z, -y);

/** Offline 3D globe: Earth-fixed or inertial view, sunlit, country borders, orbit and marker. */
export class Globe {
  constructor(container) {
    this.container = container;
    this.renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
    this.renderer.setPixelRatio(window.devicePixelRatio || 1);
    container.prepend(this.renderer.domElement);
    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(40, 1, 0.01, 100);
    this.az = 0.6; this.el = 0.35; this.dist = 4.2;
    this.earth = new THREE.Group();
    this.scene.add(this.earth);
    this.earth.add(new THREE.Mesh(new THREE.SphereGeometry(1, 192, 128),
      new THREE.MeshPhongMaterial({ color: 0x1d4a78, emissive: 0x06101c, shininess: 12, specular: 0x223344 })));
    this.scene.add(this.starfield());
    this.earth.add(this.graticule());
    this.sun = new THREE.DirectionalLight(0xffffff, 1.6);
    this.scene.add(this.sun, new THREE.AmbientLight(0x8899aa, 0.22));
    // soft light from the camera so the spacecraft model stays readable on the night side
    this.fill = new THREE.PointLight(0xffffff, 0.0, 0, 0);
    this.camera.add(this.fill);
    this.scene.add(this.camera);
    this.pathAll = null; this.pathPast = null; this.ref = null;
    this.marker = new THREE.Mesh(new THREE.SphereGeometry(0.022, 16, 12), new THREE.MeshBasicMaterial({ color: 0xffcc33 }));
    this.scene.add(this.marker);
    // formal 1-sigma uncertainty ellipsoid, exaggerated so it is visible at globe scale
    this.ell = new THREE.Mesh(new THREE.SphereGeometry(1, 20, 12), new THREE.MeshBasicMaterial({ color: 0xff8a5c, wireframe: true, transparent: true, opacity: 0.75 }));
    this.ell.matrixAutoUpdate = false;
    this.ell.visible = false;
    this.scene.add(this.ell);
    this.showEll = false; this.ellScale = 0;
    this.stations = new THREE.Group();
    this.scene.add(this.stations);
    // spacecraft model (enlarged so it is visible), follow camera
    this.craft = null; this.craftKey = null; this.follow = false;
    this.faz = 1.15; this.fel = 0.08; this.fdist = 0.034;
    this.view = 'ecef';
    this.controls();
    new ResizeObserver(() => this.resize()).observe(container);
    this.resize();
    this.pending = false;
    this.render();
  }

  starfield() {
    const pos = [];
    let seed = 7;
    const rnd = () => { seed = (seed * 16807) % 2147483647; return seed / 2147483647; };
    for (let i = 0; i < 2500; i++) {
      const u = rnd() * 2 - 1, a = rnd() * 2 * Math.PI, r = 60;
      const s = Math.sqrt(1 - u * u);
      pos.push(r * s * Math.cos(a), r * u, r * s * Math.sin(a));
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    return new THREE.Points(geo, new THREE.PointsMaterial({ color: 0xbfd2ff, size: 1.2, sizeAttenuation: false, transparent: true, opacity: 0.7 }));
  }

  /** key: 'sentinel3' | 'generic' | 'none'. */
  setModel(key) {
    if (this.craft) { this.scene.remove(this.craft); this.craft = null; }
    this.craftKey = key;
    if (key && MODELS[key]) {
      this.craft = MODELS[key].build();
      this.craft.matrixAutoUpdate = false;
      this.scene.add(this.craft);
    }
    this.setTime(this.time ?? 0);
  }

  /** Camera rides with the satellite (drag to look around it, wheel to zoom). */
  setFollow(on) {
    this.follow = on;
    if (!on) this.place();
    this.setTime(this.time ?? 0);
  }

  graticule() {
    const pts = [];
    const ring = (f) => { for (let i = 0; i < 180; i++) { pts.push(f(i / 180 * 2 * Math.PI), f((i + 1) / 180 * 2 * Math.PI)); } };
    for (let lat = -60; lat <= 60; lat += 30) {
      const p = lat * Math.PI / 180;
      ring((a) => v3(Math.cos(p) * Math.cos(a), Math.cos(p) * Math.sin(a), Math.sin(p)).multiplyScalar(1.001));
    }
    for (let lon = 0; lon < 180; lon += 30) {
      const l = lon * Math.PI / 180;
      ring((a) => v3(Math.cos(a) * Math.cos(l), Math.cos(a) * Math.sin(l), Math.sin(a)).multiplyScalar(1.001));
    }
    return new THREE.LineSegments(new THREE.BufferGeometry().setFromPoints(pts),
      new THREE.LineBasicMaterial({ color: 0x2f5b86, transparent: true, opacity: 0.45 }));
  }

  /** Render on demand (next animation frame), never in a continuous loop. */
  render() {
    if (this.pending) return;
    this.pending = true;
    requestAnimationFrame(() => { this.pending = false; this.renderer.render(this.scene, this.camera); });
  }

  setBorders(lines) {
    const pos = [];
    const d = Math.PI / 180;
    for (const l of lines) {
      for (let i = 0; i + 3 < l.length; i += 2) {
        for (const [lo, la] of [[l[i], l[i + 1]], [l[i + 2], l[i + 3]]]) {
          const c = Math.cos(la * d);
          const p = v3(c * Math.cos(lo * d), c * Math.sin(lo * d), Math.sin(la * d)).multiplyScalar(1.0015);
          pos.push(p.x, p.y, p.z);
        }
      }
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    this.earth.add(new THREE.LineSegments(g, new THREE.LineBasicMaterial({ color: 0xa9cbee, transparent: true, opacity: 0.75 })));
    this.render();
  }

  /** traj: payload from /api/runs/{id}/trajectory; ref: optional reference orbit (POEORB) in the same layout. */
  setTrajectory(traj, ref) {
    this.traj = traj;
    this.refTraj = ref || null;
    this.rebuild();
  }

  setView(v) { this.view = v; this.rebuild(); }

  /** Show/hide the exaggerated 1-sigma ellipsoid. Returns the exaggeration factor (0 if none). */
  setUncertainty(on) {
    this.showEll = on;
    const t = this.traj;
    let mx = 0;
    if (t && t.sigmaRtn) for (const s of t.sigmaRtn) if (s) mx = Math.max(mx, s[0], s[1], s[2]);
    this.ellScale = mx > 0 ? 0.045 / mx : 0; // globe units per metre: the largest sigma of the run maps to ~290 km
    this.setTime(this.time ?? 0);
    return this.ellScale * RE * 1000;
  }

  /** Ground stations: [{name, latDeg, lonDeg}] drawn as small green markers. */
  setStations(list) {
    this.stations.clear();
    const d = Math.PI / 180;
    for (const s of list || []) {
      const m = new THREE.Mesh(new THREE.SphereGeometry(0.014, 10, 8), new THREE.MeshBasicMaterial({ color: 0x3fbf74 }));
      const la = s.latDeg * d, lo = s.lonDeg * d;
      m.position.copy(v3(Math.cos(la) * Math.cos(lo), Math.cos(la) * Math.sin(lo), Math.sin(la)).multiplyScalar(1.003));
      this.stations.add(m);
    }
    this.render();
  }

  pointAt(k) {
    const t = this.traj;
    let x = t.itrf[3 * k], y = t.itrf[3 * k + 1];
    const z = t.itrf[3 * k + 2];
    if (this.view === 'inertial') { const a = t.era[k], c = Math.cos(a), s = Math.sin(a); [x, y] = [c * x - s * y, s * x + c * y]; }
    return v3(x / RE, y / RE, z / RE);
  }

  points(t, upto) {
    const out = [];
    const n = upto ?? t.t.length;
    for (let i = 0; i < n; i++) {
      let x = t.itrf[3 * i], y = t.itrf[3 * i + 1];
      const z = t.itrf[3 * i + 2];
      if (this.view === 'inertial') { const a = t.era[i], c = Math.cos(a), s = Math.sin(a); [x, y] = [c * x - s * y, s * x + c * y]; }
      out.push(v3(x / RE, y / RE, z / RE));
    }
    return out;
  }

  rebuild() {
    for (const o of [this.pathAll, this.pathPast, this.ref]) if (o) { this.scene.remove(o); o.geometry.dispose(); }
    if (!this.traj) return;
    const pts = this.points(this.traj);
    this.pathAll = new THREE.Line(new THREE.BufferGeometry().setFromPoints(pts), new THREE.LineBasicMaterial({ color: 0x4f9cf7, transparent: true, opacity: 0.35 }));
    this.pathPast = new THREE.Line(new THREE.BufferGeometry().setFromPoints(pts), new THREE.LineBasicMaterial({ color: 0x7fd3ff }));
    this.scene.add(this.pathAll, this.pathPast);
    if (this.refTraj) {
      this.ref = new THREE.Line(new THREE.BufferGeometry().setFromPoints(this.points(this.refTraj)), new THREE.LineBasicMaterial({ color: 0xc08bff, transparent: true, opacity: 0.8 }));
      this.scene.add(this.ref);
    }
    this.setTime(this.time ?? 0);
  }

  /** f: fractional sample index. */
  setTime(f) {
    this.time = f;
    const t = this.traj;
    if (!t) return;
    const n = t.t.length;
    const i = Math.max(0, Math.min(n - 1, Math.floor(f)));
    const j = Math.min(n - 1, i + 1);
    const w = Math.min(1, Math.max(0, f - i));
    const lerp = (arr, k) => arr[3 * i + k] * (1 - w) + arr[3 * j + k] * w;
    let x = lerp(t.itrf, 0), y = lerp(t.itrf, 1);
    const z = lerp(t.itrf, 2);
    const era = t.era[i] * (1 - w) + t.era[j] * w;
    let sx = lerp(t.sunItrf, 0), sy = lerp(t.sunItrf, 1);
    const sz = lerp(t.sunItrf, 2);
    if (this.view === 'inertial') {
      const c = Math.cos(era), s = Math.sin(era);
      [x, y] = [c * x - s * y, s * x + c * y];
      [sx, sy] = [c * sx - s * sy, s * sx + c * sy];
      this.earth.rotation.set(0, era, 0);
    } else {
      this.earth.rotation.set(0, 0, 0);
    }
    this.marker.position.copy(v3(x / RE, y / RE, z / RE));
    this.orientCraft(i, n);
    const sig = t.sigmaRtn && t.sigmaRtn[i];
    this.ell.visible = !!(this.showEll && sig && this.ellScale > 0);
    if (this.ell.visible) {
      const p = this.marker.position.clone();
      const R = p.clone().normalize();
      const T = this.pointAt(Math.min(n - 1, i + 1)).sub(this.pointAt(Math.max(0, i - 1)));
      T.sub(R.clone().multiplyScalar(T.dot(R))).normalize();
      const N = new THREE.Vector3().crossVectors(R, T);
      const k = this.ellScale;
      this.ell.matrix.makeBasis(R.multiplyScalar(Math.max(sig[0] * k, 1e-4)), T.multiplyScalar(Math.max(sig[1] * k, 1e-4)),
        N.multiplyScalar(Math.max(sig[2] * k, 1e-4))).setPosition(p);
      this.ell.matrixWorldNeedsUpdate = true;
    }
    this.stations.rotation.set(0, this.view === 'inertial' ? era : 0, 0);
    this.sun.position.copy(v3(sx, sy, sz).multiplyScalar(10));
    this.render();
  }

  /** Nadir-pointing attitude, flying along the velocity; scale depends on the camera mode. */
  orientCraft(i, n) {
    const p = this.marker.position.clone();
    const R = p.clone().normalize();
    const V = this.pointAt(Math.min(n - 1, i + 1)).sub(this.pointAt(Math.max(0, i - 1)));
    V.sub(R.clone().multiplyScalar(V.dot(R))).normalize();
    const Z = R.clone().negate();            // nadir
    const Y = new THREE.Vector3().crossVectors(Z, V);
    this.frame = { p, R, X: V, Y, Z };
    this.marker.visible = !this.craft || !this.follow;
    this.marker.scale.setScalar(this.craft ? 0.45 : 1);
    if (this.craft) {
      const span = MODELS[this.craftKey].span;
      const s = (this.follow ? 0.0075 : 0.09) / span; // globe units per metre (enlarged)
      this.craft.matrix.makeBasis(V.clone().multiplyScalar(s), Y.clone().multiplyScalar(s), Z.clone().multiplyScalar(s)).setPosition(p);
      this.craft.matrixWorldNeedsUpdate = true;
    }
    this.fill.intensity = this.follow ? 0.9 : 0.35;
    // following: show only the last orbit of track, not days of it
    if (this.pathAll) this.pathAll.visible = !this.follow;
    if (this.ref) this.ref.visible = !this.follow;
    if (this.pathPast) {
      const k = this.follow ? Math.max(0, i - 110) : 0;
      this.pathPast.geometry.setDrawRange(k, i + 1 - k);
    }
    if (this.follow) this.placeFollow();
  }

  placeFollow() {
    const f = this.frame;
    if (!f) return;
    const back = f.X.clone().negate(), side = f.Y;
    const off = back.multiplyScalar(Math.cos(this.faz)).add(side.clone().multiplyScalar(Math.sin(this.faz)))
      .multiplyScalar(Math.cos(this.fel) * this.fdist).add(f.R.clone().multiplyScalar(Math.sin(this.fel) * this.fdist));
    this.camera.near = 0.0004; this.camera.far = 80; this.camera.updateProjectionMatrix();
    this.camera.position.copy(f.p).add(off);
    this.camera.up.copy(f.R);
    this.camera.lookAt(f.p.clone().add(f.R.clone().multiplyScalar(-0.004)));
  }

  controls() {
    const c = this.renderer.domElement;
    let drag = null;
    c.addEventListener('pointerdown', (e) => { drag = this.follow ? [e.clientX, e.clientY, this.faz, this.fel] : [e.clientX, e.clientY, this.az, this.el]; c.setPointerCapture(e.pointerId); });
    c.addEventListener('pointermove', (e) => {
      if (!drag) return;
      if (this.follow) {
        this.faz = drag[2] - (e.clientX - drag[0]) * 0.006;
        this.fel = Math.max(-1.2, Math.min(1.45, drag[3] + (e.clientY - drag[1]) * 0.006));
        this.placeFollow(); this.render();
        return;
      }
      this.az = drag[2] - (e.clientX - drag[0]) * 0.006;
      this.el = Math.max(-1.45, Math.min(1.45, drag[3] + (e.clientY - drag[1]) * 0.006));
      this.place();
    });
    c.addEventListener('pointerup', () => { drag = null; });
    c.addEventListener('wheel', (e) => {
      e.preventDefault();
      if (this.follow) { this.fdist = Math.max(0.008, Math.min(0.4, this.fdist * (e.deltaY > 0 ? 1.1 : 0.9))); this.placeFollow(); this.render(); return; }
      this.dist = Math.max(1.4, Math.min(14, this.dist * (e.deltaY > 0 ? 1.1 : 0.9))); this.place();
    }, { passive: false });
    c.addEventListener('dblclick', () => {
      if (this.follow) { this.faz = 1.15; this.fel = 0.08; this.fdist = 0.034; this.placeFollow(); this.render(); return; }
      this.az = 0.6; this.el = 0.35; this.dist = 4.2; this.place();
    });
    this.place();
  }

  place() {
    if (this.follow) { this.placeFollow(); this.render(); return; }
    this.camera.near = 0.01; this.camera.far = 100; this.camera.updateProjectionMatrix();
    this.camera.up.set(0, 1, 0);
    this.camera.position.set(this.dist * Math.cos(this.el) * Math.sin(this.az), this.dist * Math.sin(this.el), this.dist * Math.cos(this.el) * Math.cos(this.az));
    this.camera.lookAt(0, 0, 0);
    this.render();
  }

  resize() {
    const w = this.container.clientWidth || 600, h = this.container.clientHeight || 400;
    this.renderer.setSize(w, h);
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
    this.render();
  }
}
