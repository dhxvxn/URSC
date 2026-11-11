import * as THREE from '../vendor/three.module.min.js';

/*
 * Procedural spacecraft models for the globe (no external model files, works offline).
 * Model frame, metres: +X = flight direction, +Z = nadir (towards Earth), +Y = Z x X.
 * The Sentinel-3 build is a simplified engineering likeness (main bus, single canted solar
 * wing, SRAL altimeter dish, MWR, OLCI camera fan, SLSTR scanner, GNSS antenna, star trackers);
 * dimensions are approximate and for visualisation only.
 */

const phong = (color, extra = {}) => new THREE.MeshPhongMaterial(Object.assign({ color, shininess: 40, specular: 0x333333 }, extra));

function cellTexture(cols, rows) {
  const c = document.createElement('canvas');
  c.width = 256; c.height = 256;
  const g = c.getContext('2d');
  g.fillStyle = '#16264f'; g.fillRect(0, 0, 256, 256);
  g.strokeStyle = '#5d79b8'; g.lineWidth = 2;
  for (let i = 0; i <= cols; i++) { const x = i * 256 / cols; g.beginPath(); g.moveTo(x, 0); g.lineTo(x, 256); g.stroke(); }
  for (let j = 0; j <= rows; j++) { const y = j * 256 / rows; g.beginPath(); g.moveTo(0, y); g.lineTo(256, y); g.stroke(); }
  const t = new THREE.CanvasTexture(c);
  t.anisotropy = 4;
  return t;
}

function box(w, h, d, mat, x = 0, y = 0, z = 0) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat);
  m.position.set(x, y, z);
  return m;
}

/** Cylinder along the given axis ('x' | 'y' | 'z'). */
function cyl(r, len, mat, axis, x = 0, y = 0, z = 0, seg = 20) {
  const m = new THREE.Mesh(new THREE.CylinderGeometry(r, r, len, seg), mat);
  if (axis === 'x') m.rotation.z = Math.PI / 2;
  if (axis === 'z') m.rotation.x = Math.PI / 2;
  m.position.set(x, y, z);
  return m;
}

/** Parabolic dish of radius r and depth d, opening towards +Z. */
function dish(r, d, mat) {
  const pts = [];
  for (let i = 0; i <= 16; i++) { const x = r * i / 16; pts.push(new THREE.Vector2(x, d * (x / r) ** 2)); }
  const m = new THREE.Mesh(new THREE.LatheGeometry(pts, 40), mat);
  m.rotation.x = Math.PI / 2; // lathe axis (y) -> +z, so the dish opens towards nadir (+z)
  return m;
}

function solarPanel(lenY, widX, mat) {
  return box(widX, lenY, 0.05, mat);
}

export function sentinel3() {
  const g = new THREE.Group();
  const silver = phong(0xd7dade, { shininess: 70 });
  const gold = phong(0xc89b3c, { shininess: 90, specular: 0x886622 });
  const white = phong(0xf2f2f2);
  const dark = phong(0x222428);
  const cells = new THREE.MeshPhongMaterial({ map: cellTexture(6, 16), shininess: 80, specular: 0x6688cc });
  // main bus ~3.9 x 2.2 x 2.2 m, MLI-wrapped
  g.add(box(3.9, 2.2, 2.2, silver));
  g.add(box(3.6, 0.02, 1.9, gold, 0, 1.11, 0));          // +Y side MLI
  g.add(box(3.4, 1.8, 0.02, white, 0, 0, -1.11));         // zenith radiator
  // single solar wing on -Y, canted towards the Sun
  const wing = new THREE.Group();
  wing.add(cyl(0.05, 0.9, silver, 'y', 0, -0.45, 0));
  const panel = solarPanel(5.6, 1.9, cells);
  panel.position.set(0, -0.9 - 2.8, 0);
  wing.add(panel);
  wing.position.set(-0.4, -1.1, -0.3);
  wing.rotation.y = 0;
  wing.rotation.x = -0.55;
  g.add(wing);
  // SRAL altimeter dish (1.2 m) looking at nadir
  const sral = dish(0.6, 0.22, white);
  sral.position.set(-0.6, 0.2, 1.1);
  g.add(sral, cyl(0.06, 0.4, dark, 'z', -0.6, 0.2, 1.3, 10));
  // MWR microwave radiometer
  g.add(cyl(0.22, 0.35, white, 'z', -1.45, -0.55, 1.28), cyl(0.12, 0.2, dark, 'z', -1.45, -0.55, 1.5));
  // OLCI: five cameras fanned across track
  g.add(box(0.7, 1.4, 0.45, silver, 1.3, 0, 1.33));
  for (let k = -2; k <= 2; k++) {
    const lens = cyl(0.09, 0.3, dark, 'z', 1.3, k * 0.26, 1.6, 14);
    lens.rotation.y = 0;
    lens.rotation.x = Math.PI / 2 + k * 0.19;
    g.add(lens);
  }
  // SLSTR: nadir and oblique (backward) views
  g.add(box(0.9, 0.8, 0.6, gold, 0.55, -0.6, 1.4));
  const nadir = cyl(0.16, 0.3, dark, 'z', 0.55, -0.6, 1.8);
  const oblique = cyl(0.14, 0.35, dark, 'z', 0.15, -0.6, 1.72);
  oblique.rotation.y = -0.9;
  g.add(nadir, oblique);
  // GNSS antenna and star trackers on the zenith side, DORIS and LRR on nadir
  g.add(cyl(0.18, 0.06, white, 'z', 1.2, 0.5, -1.16), cyl(0.04, 0.15, silver, 'z', 1.2, 0.5, -1.25, 8));
  for (const y of [-0.5, -0.1]) {
    const st = cyl(0.08, 0.3, dark, 'z', 1.7, y, -1.2, 12);
    st.rotation.y = 0.5;
    g.add(st);
  }
  const doris = new THREE.Mesh(new THREE.ConeGeometry(0.08, 0.45, 12), white);
  doris.rotation.x = Math.PI / 2; // tip towards nadir
  doris.position.set(-1.7, 0.7, 1.35);
  g.add(doris);
  g.add(new THREE.Mesh(new THREE.SphereGeometry(0.1, 12, 8, 0, Math.PI * 2, 0, Math.PI / 2), silver).translateX(1.75).translateY(0.8).translateZ(1.12));
  return g;
}

export function generic() {
  const g = new THREE.Group();
  const gold = phong(0xc89b3c, { shininess: 90, specular: 0x886622 });
  const silver = phong(0xd7dade, { shininess: 70 });
  const white = phong(0xf2f2f2);
  const cells = new THREE.MeshPhongMaterial({ map: cellTexture(4, 10), shininess: 80, specular: 0x6688cc });
  g.add(box(1.8, 1.6, 1.6, gold));
  for (const s of [-1, 1]) {
    g.add(cyl(0.04, 0.6, silver, 'y', 0, s * 1.1, 0));
    const p = solarPanel(4.0, 1.4, cells);
    p.position.set(0, s * 3.4, 0);
    g.add(p);
  }
  const d = dish(0.45, 0.15, white);
  d.position.set(0.3, 0, 0.8);
  g.add(d, cyl(0.03, 0.5, silver, 'z', 0.3, 0, 1.0, 8));
  g.add(cyl(0.12, 0.05, white, 'z', -0.5, 0.4, -0.83));
  return g;
}

export const MODELS = {
  sentinel3: { label: 'Sentinel-3 build', build: sentinel3, span: 9.0 },
  generic: { label: 'Generic satellite', build: generic, span: 8.8 },
};

/** Model to use for an object name: Sentinel-3A/3B get their own build, anything else the generic one. */
export function modelFor(name) {
  return /SEN(TINEL)?[-_ ]?3|^S3[AB]\b/i.test(name || '') ? 'sentinel3' : 'generic';
}
