// Erzeugt alle Cosmetics (3D-Modelle + Pixel-Texturen + Katalog) für den moonvibe-Server.
//   node tools/generate-cosmetics.mjs
//
// Modelle sind im Minecraft-Modellraum (Pixel, y zeigt nach UNTEN, -z = vorne/Gesicht).
// "head": Ursprung am Hals, Kopf von y=-8 bis 0, Gesicht bei z=-4.
// "body": Ursprung am Hals, Körper von y=0 bis 12, Rücken bei z=+2.
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { crc32, deflateSync } from 'node:zlib';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');

// ================================================================ PNG + Pixel-Hilfen
function png(width, height, pixels) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) pixels.copy(raw, y * (width * 4 + 1) + 1, y * width * 4, (y + 1) * width * 4);
  const chunk = (type, data) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(body) >>> 0);
    return Buffer.concat([len, body, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

class Texture {
  constructor(w, h) {
    this.w = w;
    this.h = h;
    this.data = Buffer.alloc(w * h * 4);
  }
  set(x, y, c) {
    if (!c || x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const i = (y * this.w + x) * 4;
    this.data[i] = clamp(c[0]);
    this.data[i + 1] = clamp(c[1]);
    this.data[i + 2] = clamp(c[2]);
    this.data[i + 3] = clamp(c[3] ?? 255);
  }
  png() {
    return png(this.w, this.h, this.data);
  }
}

const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));
const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16), 255];
const mix = (a, b, t) => a.map((v, i) => v + ((b[i] ?? 255) - v) * Math.max(0, Math.min(1, t)));
const shade = (c, f) => [c[0] * f, c[1] * f, c[2] * f, c[3] ?? 255];
const noise = (x, y, seed = 1) => {
  const n = Math.sin(x * 127.1 + y * 311.7 + seed * 74.7) * 43758.5453;
  return n - Math.floor(n);
};
const grain = (c, x, y, amount = 0.08, seed = 1) => shade(c, 1 - amount / 2 + noise(x, y, seed) * amount);
/** weiches, glattes Rauschen (für Nebel, Tarnmuster …) */
const smooth = (x, y, seed = 1, scale = 4) => {
  const xi = Math.floor(x / scale);
  const yi = Math.floor(y / scale);
  const fx = x / scale - xi;
  const fy = y / scale - yi;
  const s = (t) => t * t * (3 - 2 * t);
  const a = noise(xi, yi, seed);
  const b = noise(xi + 1, yi, seed);
  const c = noise(xi, yi + 1, seed);
  const d = noise(xi + 1, yi + 1, seed);
  return a + (b - a) * s(fx) + (c - a) * s(fy) + (a - b - c + d) * s(fx) * s(fy);
};
/** Standard-Schattierung: oben hell, Seiten mittel, unten dunkel. */
const lit = (face) => ({ top: 1.12, bottom: 0.7, north: 1, south: 0.92, west: 0.86, east: 0.86 })[face];

// ---------- Farben ----------
const C = {
  blue: hex('#2e9bff'),
  blueDark: hex('#0b3f8f'),
  gold: hex('#ffb627'),
  goldLight: hex('#ffe08a'),
  goldDark: hex('#c97a00'),
  black: hex('#1b1d24'),
  white: hex('#f4f6fb'),
  red: hex('#e11d48'),
  pink: hex('#f472b6'),
  brown: hex('#7a4a26'),
  silver: hex('#cbd5e1'),
  cyan: hex('#22d3ee'),
};

// ---------- Maler (paint-Funktionen für paintBox) ----------
const P = {
  solid: (c, seed = 1, amt = 0.08) => (f, x, y) => grain(shade(c, lit(f)), x, y, amt, seed),
  vgrad: (top, bottom, seed = 1) => (f, x, y, w, h) => grain(shade(mix(top, bottom, h > 1 ? y / (h - 1) : 0), lit(f)), x, y, 0.08, seed),
  glow: (c, c2 = c, seed = 1) => (f, x, y) => mix(c, c2, noise(x, y, seed)),
};

// ================================================================ Modell-Bauer mit automatischer Textur-Anordnung
class Atlas {
  constructor(size) {
    this.size = size;
    this.x = 0;
    this.y = 0;
    this.row = 0;
  }
  alloc(w, h) {
    if (this.x + w > this.size) {
      this.x = 0;
      this.y += this.row;
      this.row = 0;
    }
    if (this.y + h > this.size || w > this.size) return null;
    const pos = [this.x, this.y];
    this.x += w;
    this.row = Math.max(this.row, h);
    return pos;
  }
}

/** Bemalt die Box-UV-Fläche eines Würfels genau so, wie Minecraft sie anordnet. */
function paintBox(tex, [u, v], [w, h, d], paint) {
  const faces = [
    ['top', u + d, v, w, d],
    ['bottom', u + d + w, v, w, d],
    ['west', u, v + d, d, h],
    ['north', u + d, v + d, w, h],
    ['east', u + d + w, v + d, d, h],
    ['south', u + d + w + d, v + d, w, h],
  ];
  for (const [face, fx, fy, fw, fh] of faces) {
    for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) tex.set(fx + x, fy + y, paint(face, x, y, fw, fh));
  }
}

/** Würfel: from/size in Modell-Pixeln, paint = Maler; extra.share = gleiche Textur wie ein anderer Würfel */
const box = (from, size, paint, extra = {}) => ({ from, size, paint, ...extra });

/** Baut aus Teilen ein Modell (JSON) + Textur; Würfel bekommen automatisch Platz in der Textur. */
function build(attach, parts) {
  for (const size of [32, 64, 128]) {
    const atlas = new Atlas(size);
    const tex = new Texture(size, size);
    let ok = true;
    const uvs = new Map();
    const convert = (part) => ({
      pivot: part.pivot,
      rotation: part.rotation,
      animation: part.animation,
      emissive: part.emissive || undefined,
      cubes: (part.cubes ?? []).map((c) => {
        let uv = c.share ? uvs.get(c.share) : undefined;
        if (!uv) {
          const [w, h, d] = c.size.map(Math.ceil);
          uv = atlas.alloc(2 * (w + d), d + h);
          if (!uv) {
            ok = false;
            uv = [0, 0];
          } else paintBox(tex, uv, [w, h, d], c.paint ?? c.share?.paint);
          uvs.set(c, uv);
        }
        return { from: c.from, size: c.size, uv, inflate: c.inflate || undefined, mirror: c.mirror || undefined };
      }),
      children: part.children?.map(convert),
    });
    const json = { attach, textureWidth: size, textureHeight: size, parts: parts.map(convert) };
    if (ok) return { json, tex };
  }
  throw new Error('Textur zu groß');
}

// ================================================================ Ausgabe
const FOLDERS = { cape: 'capes', hat: 'hats', face: 'face', back: 'back', wings: 'wings', shoes: 'shoes', pet: 'pets' };
rmSync(join(ROOT, 'models'), { recursive: true, force: true });
mkdirSync(join(ROOT, 'models'), { recursive: true });
for (const dir of Object.values(FOLDERS)) {
  rmSync(join(ROOT, 'textures', dir), { recursive: true, force: true });
  mkdirSync(join(ROOT, 'textures', dir), { recursive: true });
}

const catalog = [];
const N = (en, de, fr, es, tr, ru) => ({ en, de, fr, es, tr, ru });
function add(type, id, names, rarity, tex, model) {
  writeFileSync(join(ROOT, 'textures', FOLDERS[type], `${id}.png`), tex.png());
  if (model) writeFileSync(join(ROOT, 'models', `${id}.json`), JSON.stringify(model));
  catalog.push({ id, type, rarity, names });
}
function addModel(type, id, names, rarity, attach, parts) {
  const { json, tex } = build(attach, parts);
  add(type, id, names, rarity, tex, json);
}

// ================================================================ CAPES (64×32, Vorderseite x1–10, y1–16)
function capeTexture(design) {
  const tex = new Texture(64, 32);
  paintBox(tex, [0, 0], [10, 16, 1], (face, x, y, w) => {
    if (face === 'north' || face === 'south') return design(face === 'north' ? x : w - 1 - x, y, face === 'north');
    return shade(design(0, Math.min(y, 15), false), 0.75);
  });
  // Elytra-Bereich (Minecraft nutzt die Cape-Textur auch für Elytren)
  for (let y = 0; y < 22; y++) for (let x = 22; x < 46; x++) tex.set(x, y, design((x - 22) % 10, Math.min(15, Math.floor(y * 0.75)), true));
  return tex;
}
const LETTERS = { E: ['111', '100', '110', '100', '111'], Z: ['111', '001', '010', '100', '111'] };
const glyph = (L, x, y, ox, oy) => LETTERS[L][y - oy]?.[x - ox] === '1';
const border = (x, y) => x === 0 || x === 9 || y === 0 || y === 15;
// Round pixel moon (radius in pixels) with a few craters
const MOON = { cream: hex('#ece8dc'), shadow: hex('#b9b3a4'), crater: hex('#a59f90'), navy: hex('#1b1f3b'), night: hex('#0b0d1c'), lilac: hex('#8b93ff') };
function moonPixel(x, y, cx, cy, r) {
  const dx = x + 0.5 - cx;
  const dy = y + 0.5 - cy;
  if (dx * dx + dy * dy > r * r) return null;
  const crater = (ax, ay, ar) => (x + 0.5 - (cx + ax)) ** 2 + (y + 0.5 - (cy + ay)) ** 2 <= ar * ar;
  if (crater(-1.3, 1.1, 0.75) || crater(1.4, -0.9, 0.6)) return mix(MOON.crater, MOON.shadow, 0.35);
  return mix(MOON.cream, MOON.shadow, Math.max(0, (dx + dy) / (2 * r) + 0.25));
}

const capes = [
  ['moonvibe', N('Moonvibe', 'Moonvibe', 'Moonvibe', 'Moonvibe', 'Moonvibe', 'Moonvibe'), 'legendary', (x, y, front) => {
    let c = grain(mix(MOON.navy, MOON.night, y / 15), x, y);
    if ((x * 7 + y * 13) % 23 === 0) c = hex('#dfe3ff');
    if (border(x, y)) c = grain(MOON.lilac, x, y, 0.1);
    const m = front ? moonPixel(x, y, 5, 6.5, 3.4) : null;
    return m ? grain(m, x, y, 0.06, 3) : c;
  }],
  ['moonvibe_gold', N('Moonvibe Gold', 'Moonvibe Gold', 'Moonvibe Or', 'Moonvibe Oro', 'Moonvibe Altın', 'Moonvibe Золото'), 'legendary', (x, y, front) => {
    let c = grain(mix(C.goldLight, C.goldDark, y / 15), x, y);
    if (border(x, y)) c = grain(MOON.navy, x, y, 0.1);
    const m = front ? moonPixel(x, y, 5, 6.5, 3.4) : null;
    return m ? grain(mix(m, MOON.navy, 0.15), x, y, 0.06, 3) : c;
  }],
  ['midnight', N('Midnight', 'Mitternacht', 'Minuit', 'Medianoche', 'Gece Yarısı', 'Полночь'), 'rare',
    (x, y) => ((x * 73 + y * 151) % 37 === 0 ? [255, 255, 230, 255] : grain(mix(hex('#1e1b4b'), hex('#020617'), y / 15), x, y))],
  ['flame', N('Flame', 'Flamme', 'Flamme', 'Llama', 'Alev', 'Пламя'), 'epic',
    (x, y) => grain(mix(hex('#facc15'), hex('#b91c1c'), y / 15 + Math.sin(x * 1.3) * 0.08), x, y, 0.15)],
  ['ocean', N('Ocean', 'Ozean', 'Océan', 'Océano', 'Okyanus', 'Океан'), 'rare',
    (x, y) => (Math.sin((y + Math.sin(x * 0.9) * 1.5) * 1.1) > 0.6 ? mix(hex('#a5f3fc'), hex('#38bdf8'), y / 15) : grain(mix(hex('#0ea5e9'), hex('#1e3a8a'), y / 15), x, y))],
  ['emerald', N('Emerald', 'Smaragd', 'Émeraude', 'Esmeralda', 'Zümrüt', 'Изумруд'), 'common',
    (x, y) => grain((x + y) % 4 < 2 ? mix(hex('#34d399'), hex('#065f46'), y / 15) : mix(hex('#10b981'), hex('#064e3b'), y / 15), x, y)],
  ['royal_gold', N('Royal Gold', 'Königsgold', 'Or royal', 'Oro real', 'Kraliyet Altını', 'Королевское золото'), 'epic',
    (x, y) => (border(x, y) ? grain(C.gold, x, y) : (x === 4 || x === 5) && y > 2 && y < 13 ? grain(C.goldDark, x, y) : grain(mix(hex('#1c1917'), hex('#0c0a09'), y / 15), x, y))],
  ['sakura', N('Sakura', 'Sakura', 'Sakura', 'Sakura', 'Sakura', 'Сакура'), 'rare',
    (x, y) => ((x * 7 + y * 13) % 11 === 0 ? hex('#ffffff') : grain(mix(hex('#fbcfe8'), hex('#db2777'), y / 15), x, y))],
  ['galaxy', N('Galaxy', 'Galaxie', 'Galaxie', 'Galaxia', 'Galaksi', 'Галактика'), 'legendary', (x, y) => {
    if (noise(x, y, 41) > 0.93) return hex('#ffffff');
    const n = smooth(x, y, 7, 3);
    return grain(mix(mix(hex('#0b0628'), hex('#5b21b6'), n), hex('#ec4899'), Math.max(0, n - 0.7) * 2), x, y, 0.1);
  }],
  ['sunset', N('Sunset', 'Sonnenuntergang', 'Coucher de soleil', 'Atardecer', 'Gün Batımı', 'Закат'), 'rare', (x, y) => {
    const sun = (x - 4.5) ** 2 + (y - 9) ** 2 < 7;
    return sun ? hex('#fde68a') : grain(mix(mix(hex('#f97316'), hex('#db2777'), y / 10), hex('#4c1d95'), (y - 8) / 8), x, y);
  }],
  ['aurora', N('Aurora', 'Polarlicht', 'Aurore boréale', 'Aurora', 'Kutup Işıkları', 'Северное сияние'), 'epic', (x, y) => {
    const band = Math.sin(x * 0.7 + y * 0.4) * 2 + 6;
    const t = 1 - Math.min(1, Math.abs(y - band) / 4);
    return grain(mix(hex('#020617'), mix(hex('#34d399'), hex('#a78bfa'), x / 9), t), x, y);
  }],
  ['lava', N('Lava', 'Lava', 'Lave', 'Lava', 'Lav', 'Лава'), 'epic', (x, y) => {
    const crack = Math.abs(smooth(x, y, 13, 2.5) - 0.5) < 0.08;
    return crack ? mix(hex('#fde047'), hex('#f97316'), noise(x, y, 2)) : grain(mix(hex('#3f0d0d'), hex('#1c0606'), y / 15), x, y, 0.15);
  }],
  ['frost', N('Frost', 'Frost', 'Givre', 'Escarcha', 'Ayaz', 'Иней'), 'rare', (x, y) => {
    const crystal = (x + y) % 5 === 0 && noise(x, y, 4) > 0.4;
    return crystal ? hex('#ffffff') : grain(mix(hex('#e0f2fe'), hex('#38bdf8'), y / 15), x, y);
  }],
  ['matrix', N('Matrix', 'Matrix', 'Matrice', 'Matrix', 'Matris', 'Матрица'), 'epic', (x, y) => {
    const head = Math.floor(noise(x, 0, 6) * 16);
    const d = (y - head + 16) % 16;
    return d < 6 ? mix(hex('#bbf7d0'), hex('#15803d'), d / 6) : grain(hex('#03120a'), x, y, 0.2);
  }],
  ['rainbow', N('Rainbow', 'Regenbogen', 'Arc-en-ciel', 'Arcoíris', 'Gökkuşağı', 'Радуга'), 'rare', (x, y) => {
    const colors = ['#ef4444', '#f97316', '#facc15', '#22c55e', '#3b82f6', '#8b5cf6'];
    return grain(hex(colors[Math.min(5, Math.floor(y / 2.67))]), x, y);
  }],
  ['checkered', N('Racing', 'Rennflagge', 'Damier', 'Bandera de carreras', 'Yarış Bayrağı', 'Гоночный флаг'), 'common',
    (x, y) => ((Math.floor(x / 2) + Math.floor(y / 2)) % 2 ? hex('#111111') : hex('#f5f5f5'))],
  ['camo', N('Camo', 'Tarnung', 'Camouflage', 'Camuflaje', 'Kamuflaj', 'Камуфляж'), 'common', (x, y) => {
    const n = smooth(x, y, 21, 2.5);
    return grain(n < 0.35 ? hex('#3f4d2c') : n < 0.6 ? hex('#5b6b3a') : n < 0.8 ? hex('#7d6b45') : hex('#262a1d'), x, y);
  }],
  ['toxic', N('Toxic', 'Giftig', 'Toxique', 'Tóxico', 'Zehirli', 'Токсичный'), 'rare', (x, y) => {
    const drip = y < 3 + Math.floor(noise(x, 1, 8) * 9);
    return drip ? grain(mix(hex('#a3e635'), hex('#65a30d'), y / 10), x, y) : grain(hex('#14200a'), x, y, 0.15);
  }],
  ['vaporwave', N('Vaporwave', 'Vaporwave', 'Vaporwave', 'Vaporwave', 'Vaporwave', 'Вейпорвейв'), 'epic', (x, y) =>
    (y > 8 && (x % 3 === 0 || y % 2 === 0) ? hex('#22d3ee') : grain(mix(hex('#f472b6'), hex('#6d28d9'), y / 15), x, y))],
  ['honey', N('Honey', 'Honig', 'Miel', 'Miel', 'Bal', 'Мёд'), 'common', (x, y) => {
    const edge = y % 3 === 0 || (x + (Math.floor(y / 3) % 2) * 2) % 4 === 0;
    return grain(edge ? hex('#b45309') : mix(hex('#fcd34d'), hex('#f59e0b'), y / 15), x, y);
  }],
  ['void', N('Void', 'Leere', 'Néant', 'Vacío', 'Boşluk', 'Пустота'), 'epic', (x, y) => {
    const d = Math.min(x, 9 - x, y, 15 - y);
    return d === 0 ? hex('#a855f7') : d === 1 ? hex('#581c87') : grain(hex('#05030a'), x, y, 0.3);
  }],
  ['cherry', N('Cherry', 'Kirsche', 'Cerise', 'Cereza', 'Kiraz', 'Вишня'), 'common',
    (x, y) => ((x + (y % 4 < 2 ? 0 : 2)) % 4 === 1 && y % 2 === 1 ? hex('#fff1f2') : grain(hex('#be123c'), x, y))],
  ['diamond', N('Diamond', 'Diamant', 'Diamant', 'Diamante', 'Elmas', 'Алмаз'), 'legendary', (x, y) => {
    const dx = Math.abs(((x + 2.5) % 5) - 2.5);
    const dy = Math.abs(((y + 2.5) % 5) - 2.5);
    return dx + dy < 2 ? mix(hex('#ecfeff'), hex('#67e8f9'), (dx + dy) / 2) : grain(hex('#0e7490'), x, y);
  }],
  ['storm', N('Storm', 'Gewitter', 'Tempête', 'Tormenta', 'Fırtına', 'Гроза'), 'rare', (x, y) => {
    const bolt = [3, 4, 4, 5, 5, 4, 4, 5, 6, 6, 5, 5, 6, 7, 7, 7];
    if (x === bolt[y] || (x === bolt[y] + 1 && y % 3 === 0)) return hex('#fde047');
    return grain(mix(hex('#475569'), hex('#0f172a'), y / 15), x, y, 0.15);
  }],
];
for (const [id, names, rarity, design] of capes) add('cape', id, names, rarity, capeTexture(design));

// ================================================================ HD-CAPES (4-fache Auflösung, weich abgetastet)
// Motiv-Funktion bekommt Kommazahlen: u 0–10 (links → rechts von hinten gesehen), v 0–16 (oben → unten).
const HD = 4;
const SUB = [[0.25, 0.25], [0.75, 0.25], [0.25, 0.75], [0.75, 0.75]];
const smoothstep = (a, b, x) => {
  const t = Math.max(0, Math.min(1, (x - a) / (b - a)));
  return t * t * (3 - 2 * t);
};
/** mehrschichtiges Rauschen (Wolken, Feuer, Nebel) */
const fbm = (x, y, seed, octaves = 4) => {
  let value = 0;
  let amp = 0.5;
  let sum = 0;
  for (let i = 0, f = 1; i < octaves; i++, f *= 2, amp *= 0.5) {
    value += smooth(x * f, y * f, seed + i * 17, 1) * amp;
    sum += amp;
  }
  return value / sum;
};
/** Abstand eines Punkts zu einer Strecke */
const seg = (px, py, ax, ay, bx, by) => {
  const dx = bx - ax;
  const dy = by - ay;
  const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
  return Math.hypot(px - ax - dx * t, py - ay - dy * t);
};
const hsl = (h, s, l) => {
  const k = (n) => (n + h * 12) % 12;
  const a = s * Math.min(l, 1 - l);
  const f = (n) => l - a * Math.max(-1, Math.min(k(n) - 3, 9 - k(n), 1));
  return [f(0) * 255, f(8) * 255, f(4) * 255, 255];
};
const glowAdd = (c, g, t) => [c[0] + g[0] * t, c[1] + g[1] * t, c[2] + g[2] * t, 255];
/** Sternenfeld: in jeder Zelle vielleicht ein Stern mit weichem Schein */
function stars(c, u, v, seed, density = 3, size = 0.12, chance = 0.45) {
  const cu = Math.floor(u * density);
  const cv = Math.floor(v * density);
  let glow = 0;
  for (let dy = -1; dy <= 1; dy++) {
    for (let dx = -1; dx <= 1; dx++) {
      const gx = cu + dx;
      const gy = cv + dy;
      if (noise(gx, gy, seed + 7) > chance) continue;
      const b = noise(gx, gy, seed + 5);
      const d = Math.hypot(u - (gx + noise(gx, gy, seed)) / density, v - (gy + noise(gx, gy, seed + 3)) / density);
      glow += Math.max(0, 1 - d / (size * (0.5 + b))) ** 2 * (0.4 + b);
    }
  }
  return mix(c, C.white, Math.min(1, glow));
}

function capeTextureHD(design) {
  const tex = new Texture(64 * HD, 32 * HD);
  // Bereich im 64×32-Raster bemalen; fn bekommt Koordinaten in Cape-Pixeln innerhalb des Bereichs
  const region = (x0, y0, w, h, fn) => {
    for (let py = 0; py < h * HD; py++) {
      for (let px = 0; px < w * HD; px++) {
        const acc = [0, 0, 0, 0];
        for (const [ox, oy] of SUB) {
          const c = fn((px + ox) / HD, (py + oy) / HD);
          for (let i = 0; i < 4; i++) acc[i] += (c[i] ?? 255) / SUB.length;
        }
        tex.set(x0 * HD + px, y0 * HD + py, acc);
      }
    }
  };
  region(1, 1, 10, 16, (u, v) => design(u, v, true)); // außen
  region(12, 1, 10, 16, (u, v) => shade(design(10 - u, v, false), 0.78)); // innen, gespiegelt
  region(1, 0, 10, 1, (u) => shade(design(u, 0.3, false), 0.7)); // oben
  region(11, 0, 10, 1, (u) => shade(design(u, 15.7, false), 0.7)); // unten
  region(0, 1, 1, 16, (_, v) => shade(design(0.2, v, false), 0.7)); // links
  region(11, 1, 1, 16, (_, v) => shade(design(9.8, v, false), 0.7)); // rechts
  region(22, 0, 24, 22, (x, y) => design(((x % 12) / 12) * 10, (y / 22) * 16, true)); // Elytra
  return tex;
}

const fireRamp = (t) =>
  t < 0.25 ? mix(hex('#0a0302'), hex('#5c0b05'), t / 0.25)
    : t < 0.5 ? mix(hex('#5c0b05'), hex('#e0400b'), (t - 0.25) / 0.25)
      : t < 0.75 ? mix(hex('#e0400b'), hex('#ffb31a'), (t - 0.5) / 0.25)
        : mix(hex('#ffb31a'), hex('#fff6c2'), (t - 0.75) / 0.25);

const hdCapes = [
  ['nebula', N('Nebula', 'Nebula', 'Nébuleuse', 'Nebulosa', 'Bulutsu', 'Туманность'), 'legendary', (u, v) => {
    const n = fbm(u * 0.4, v * 0.4, 3, 5);
    const m = fbm(u * 0.28 + 5, v * 0.28, 9);
    let c = mix(hex('#05031a'), hex('#3b0d6e'), smoothstep(0.3, 0.75, n));
    c = mix(c, hex('#ff4fa3'), smoothstep(0.55, 0.85, m) * 0.75);
    c = mix(c, hex('#4cc9ff'), smoothstep(0.35, 0.6, n * m * 1.2) * 0.55);
    return stars(c, u, v, 1, 2.6, 0.13);
  }],
  ['ender', N('Ender', 'Ender', 'Ender', 'Ender', 'Ender', 'Эндер'), 'epic', (u, v) => {
    let c = mix(hex('#0d0617'), hex('#2a0f45'), fbm(u * 0.5, v * 0.5, 31));
    if (noise(Math.floor(u * 2.5), Math.floor(v * 2.5), 33) > 0.94) c = mix(c, hex('#d16bff'), 0.75);
    const r = Math.hypot((u - 5) / 3.4, (v - 7.5) / 2.3);
    c = mix(c, hex('#a855f7'), Math.max(0, 1 - Math.abs(r - 1.08) * 3.5) * 0.7);
    if (r < 1) {
      const iris = Math.hypot(u - 5, v - 7.5) / 1.6;
      let e = mix(hex('#2aa37e'), hex('#0b3d2e'), r);
      if (iris < 1) e = mix(hex('#9cfbd8'), hex('#1fb58a'), iris);
      if (iris < 1 && Math.abs(u - 5) < 0.38 * (1 - Math.abs(v - 7.5) / 1.6)) e = hex('#06110d');
      c = mix(c, e, smoothstep(1, 0.93, r));
    }
    return c;
  }],
  ['dragon', N('Dragon', 'Drache', 'Dragon', 'Dragón', 'Ejderha', 'Дракон'), 'legendary', (u, v) => {
    const sw = 2;
    const sh = 1.4;
    const row = Math.floor(v / sh);
    const off = (row % 2) * (sw / 2);
    const lx = (u + off) / sw - Math.floor((u + off) / sw) - 0.5;
    const ly = v / sh - row;
    const d = Math.hypot(lx * 2, ly * 1.05);
    let c = d > 0.93 ? hex('#160101') : mix(hex('#ff4a2e'), hex('#4a0505'), Math.min(1, d * 1.1));
    c = mix(c, hex('#ffc35c'), Math.max(0, 0.22 - Math.abs(d - 0.35)) * 2.5);
    return shade(c, 0.6 + 0.5 * (1 - v / 16));
  }],
  ['retrowave', N('Retrowave', 'Retrowave', 'Retrowave', 'Retrowave', 'Retrowave', 'Ретровейв'), 'epic', (u, v) => {
    const horizon = 9.6;
    if (v < horizon) {
      let c = stars(mix(hex('#14042e'), hex('#ff2e88'), (v / horizon) ** 1.4), u, v, 21, 2, 0.1, 0.25);
      const d = Math.hypot(u - 5, v - 7.4);
      const gap = v > 6.4 && (v * 2.4) % 1 < (v - 6.4) / 4.5;
      if (d < 3.1 && !gap) c = mix(hex('#ffe95c'), hex('#ff4f9a'), (v - 4.3) / 5);
      return mix(c, hex('#ff7ac0'), d > 3.1 ? Math.max(0, 1 - (d - 3.1) / 1.2) * 0.35 : 0);
    }
    const dy = v - horizon + 0.12;
    const z = 6 / dy;
    const x = ((u - 5) * z) / 6;
    const lineH = Math.min(z % 1, 1 - (z % 1)) * ((dy * dy) / 6);
    const lineV = Math.min(Math.abs(x) % 1, 1 - (Math.abs(x) % 1)) * dy;
    const line = Math.max(0, 1 - Math.min(lineH, lineV) / 0.09);
    const c = mix(hex('#2b0a4a'), hex('#0d0221'), (v - horizon) / 6.4);
    return mix(mix(c, hex('#ff5fb0'), Math.max(0, 1 - dy / 1.2) * 0.4), hex('#3ef2ff'), line);
  }],
  ['inferno', N('Inferno', 'Inferno', 'Inferno', 'Infierno', 'Cehennem', 'Инферно'), 'legendary', (u, v) => {
    const n = fbm(u * 0.55, v * 0.28, 41, 5);
    return fireRamp(Math.max(0, Math.min(1, n * 1.55 + (v / 16) * 1.15 - 0.95)));
  }],
  ['deep_sea', N('Deep Sea', 'Tiefsee', 'Abysses', 'Mar profundo', 'Derin Deniz', 'Глубины'), 'rare', (u, v) => {
    let c = mix(hex('#1aa3c8'), hex('#04122e'), (v / 16) ** 0.8);
    c = mix(c, hex('#bff6ff'), Math.max(0, Math.sin((u + v * 0.35) * 1.3) - 0.55) * (1 - v / 16) * 0.9);
    const gx = Math.floor(u / 1.6);
    const gy = Math.floor(v / 1.6);
    if (noise(gx, gy, 91) < 0.4) {
      const r = 0.18 + noise(gx, gy, 92) * 0.3;
      const d = Math.hypot(u - (gx + 0.5) * 1.6, v - (gy + 0.5) * 1.6);
      if (Math.abs(d - r) < 0.07) c = mix(c, hex('#d9fbff'), 0.75);
      if (Math.hypot(u - (gx + 0.5) * 1.6 + r * 0.4, v - (gy + 0.5) * 1.6 + r * 0.4) < r * 0.25) c = mix(c, C.white, 0.8);
    }
    const sand = 14.4 + Math.sin(u * 1.4) * 0.35;
    if (v > sand) c = grain(mix(hex('#c2a36b'), hex('#7a6038'), (v - sand) / 1.6), Math.floor(u * 4), Math.floor(v * 4), 0.15);
    return c;
  }],
  ['carbon', N('Carbon', 'Carbon', 'Carbone', 'Carbono', 'Karbon', 'Карбон'), 'rare', (u, v) => {
    // Köperbindung: abwechselnd waagerechte und senkrechte Fasern mit Glanzkante
    const s = 0.75;
    const cx = Math.floor(u / s);
    const cy = Math.floor(v / s);
    const g = (cx + cy) % 2 === 0 ? u / s - cx : v / s - cy;
    let c = mix(hex('#555d69'), hex('#0b0d10'), Math.abs(g - 0.5) * 2);
    c = mix(c, hex('#8a94a3'), Math.max(0, 1 - Math.abs(u * 0.6 - v * 0.35 - 1) / 1.5) * 0.25);
    if (Math.abs(u - 3.7) < 0.42 || Math.abs(u - 6.3) < 0.42) c = mix(hex('#4cb5ff'), hex('#1f6fe0'), v / 16);
    if (Math.abs(Math.abs(u - 5) - 1.3) < 0.12) c = mix(c, C.white, 0.6);
    return c;
  }],
  ['hexcore', N('Hexcore', 'Hexcore', 'Hexcore', 'Hexcore', 'Hexcore', 'Гексокор'), 'epic', (u, v) => {
    const x = u * 0.75;
    const y = v * 0.75;
    const mod = (a, b) => a - b * Math.floor(a / b);
    const a = [mod(x, 1) - 0.5, mod(y, 1.732) - 0.866];
    const b = [mod(x - 0.5, 1) - 0.5, mod(y - 0.866, 1.732) - 0.866];
    const p = Math.hypot(...a) < Math.hypot(...b) ? a : b;
    const id = [Math.round((x - p[0]) * 2), Math.round((y - p[1]) * 2)];
    const hexDist = Math.max(Math.abs(p[0]) * 0.5 + Math.abs(p[1]) * 0.866, Math.abs(p[0]));
    const edge = 0.5 - hexDist;
    const lit = noise(id[0], id[1], 101);
    let c = mix(hex('#071226'), hex('#0e2547'), v / 16);
    if (lit > 0.82) c = mix(c, hex('#1b6fd8'), 0.55 + 0.45 * smoothstep(0.5, 0, hexDist));
    else if (lit < 0.08) c = mix(c, hex('#8b5cf6'), 0.45);
    return mix(c, hex('#5ff2ff'), smoothstep(0.07, 0.0, edge));
  }],
  ['circuit', N('Circuit', 'Platine', 'Circuit', 'Circuito', 'Devre', 'Схема'), 'rare', (u, v) => {
    let c = mix(hex('#0f4a2a'), hex('#062514'), v / 16);
    const s = 1.25;
    const gx = Math.floor(u / s);
    const gy = Math.floor(v / s);
    const lx = u / s - gx - 0.5;
    const ly = v / s - gy - 0.5;
    const r = noise(gx, gy, 51);
    const w = 0.08;
    const line = r < 0.32 ? Math.abs(ly) < w
      : r < 0.62 ? Math.abs(lx) < w
        : r < 0.84 ? (Math.abs(ly) < w && lx < w) || (Math.abs(lx) < w && ly > -w)
          : false;
    if (line) c = hex('#46e08e');
    const d = Math.hypot(lx, ly);
    if (r >= 0.84 && d < 0.24) c = d < 0.1 ? hex('#041a0d') : hex('#e8b84a');
    return c;
  }],
  ['lovestruck', N('Lovestruck', 'Verliebt', 'Coup de foudre', 'Enamorado', 'Aşık', 'Влюблённость'), 'rare', (u, v) => {
    let c = mix(hex('#ff9fd0'), hex('#c2185b'), v / 16);
    const s = 2.5;
    const row = Math.floor(v / s);
    const off = (row % 2) * (s / 2);
    const x = ((u + off) / s - Math.floor((u + off) / s) - 0.5) * 2.7;
    const y = -(v / s - row - 0.5) * 2.7 + 0.2;
    const f = (x * x + y * y - 1) ** 3 - x * x * y ** 3;
    if (f <= 0) c = mix(hex('#fff2f8'), hex('#ffb3d6'), (1 - y) / 2.4);
    else if (f < 0.25) c = mix(c, hex('#7a0c3a'), 0.35);
    return c;
  }],
  ['moon_neon', N('Moonvibe Neon', 'Moonvibe Neon', 'Moonvibe Néon', 'Moonvibe Neón', 'Moonvibe Neon', 'Moonvibe Неон'), 'legendary', (u, v) => {
    let c = mix(hex('#03060d'), hex('#0a1734'), v / 16);
    if (Math.min(u % 1, v % 1) < 0.05) c = mix(c, hex('#1d3a6e'), 0.5);
    // neon moon ring
    const d = Math.abs(Math.hypot(u - 5, v - 7.4) - 3);
    const line = seg(u, v, 1.6, 12.7, 8.4, 12.7);
    c = glowAdd(c, hex('#8b93ff'), Math.exp(-d * 2.4) * 0.95);
    c = glowAdd(c, hex('#ece8dc'), Math.exp(-line * 4) * 0.7);
    if (d < 0.2) c = mix(hex('#bfeaff'), C.white, 1 - d / 0.2);
    if (line < 0.1) c = mix(hex('#ffe08a'), C.white, 1 - line / 0.1);
    return c;
  }],
  ['moonlight', N('Moonlight', 'Mondschein', 'Clair de lune', 'Luz de luna', 'Ay Işığı', 'Лунный свет'), 'epic', (u, v) => {
    let c = stars(mix(hex('#0b1033'), hex('#3a1c5c'), v / 16), u, v, 61, 2.2, 0.1, 0.4);
    const d = Math.hypot(u - 6.2, v - 4.2);
    if (d < 2.1) {
      c = mix(hex('#fffbe6'), hex('#d8d2b4'), d / 2.1);
      c = mix(c, hex('#b8b199'), smoothstep(0.55, 0.7, fbm(u * 1.2, v * 1.2, 63)) * 0.6);
    } else c = mix(c, hex('#c7c3ff'), Math.max(0, 1 - (d - 2.1) / 1.8) * 0.35);
    const far = 11 + Math.sin(u * 0.9 + 1) * 1.1 + Math.sin(u * 2.3) * 0.4;
    if (v > far) c = mix(hex('#2a1847'), hex('#1a0f30'), (v - far) / 4);
    const near = 13.1 + Math.sin(u * 1.3 + 3) * 0.9 + Math.sin(u * 3.1) * 0.3;
    if (v > near) c = hex('#0c0718');
    return c;
  }],
  ['halloween', N('Halloween', 'Halloween', 'Halloween', 'Halloween', 'Cadılar Bayramı', 'Хэллоуин'), 'epic', (u, v) => {
    let c = mix(hex('#2a0d3d'), hex('#08020f'), v / 16);
    const x = u - 5;
    const y = v - 9.6;
    const body = (x / 3.9) ** 2 + (y / 3.1) ** 2;
    c = mix(c, hex('#ff7a1a'), Math.max(0, 1 - body / 2.2) * 0.45);
    // Fledermäuse
    for (const [bx, by, s] of [[2, 2.6, 1], [7.6, 1.8, 0.8], [8.3, 4, 0.6]]) {
      const px = (u - bx) / s;
      const py = (v - by) / s;
      const wing = Math.abs(px) < 1.2 && py > -0.35 + Math.abs(px) * 0.25 - Math.max(0, Math.abs(px) - 0.6) * 0.6 && py < 0.25 - ((Math.abs(px) * 3) % 1) * 0.25;
      if (wing || Math.hypot(px, py) < 0.28) c = hex('#050208');
    }
    if (body < 1) {
      const rib = Math.cos((x / 3.9) * Math.PI * 2.5) * 0.5 + 0.5;
      c = mix(hex('#ff8a1f'), hex('#c2410c'), rib * 0.55 + body * 0.35);
      const eye = (cx) => Math.abs(u - cx) < (v - 7.4) * 0.75 && v < 9.1;
      const mouth = Math.abs(x) < 2.3 - Math.abs(y - 1.3) * 0.4 && v > 10.3 + ((u * 1.4) % 1 < 0.5 ? 0.45 : 0) && v < 11.7 - ((u * 1.4 + 0.5) % 1 < 0.5 ? 0.4 : 0);
      if (eye(3.6) || eye(6.4) || (Math.abs(x) < 0.3 && v > 9.3 && v < 9.9) || mouth) c = mix(hex('#fff3a3'), hex('#ffb000'), (v - 7.4) / 4.5);
    }
    if (Math.abs(u - 5.1) < 0.35 && v > 5.6 && v < 6.9) c = hex('#3f6212');
    return c;
  }],
  ['amethyst', N('Amethyst', 'Amethyst', 'Améthyste', 'Amatista', 'Ametist', 'Аметист'), 'epic', (u, v) => {
    const s = 1.7;
    const gx = Math.floor(u / s);
    const gy = Math.floor(v / s);
    let d1 = 99;
    let d2 = 99;
    let id = 0;
    for (let dy = -1; dy <= 1; dy++) {
      for (let dx = -1; dx <= 1; dx++) {
        const cx = gx + dx;
        const cy = gy + dy;
        const d = Math.hypot(u - (cx + noise(cx, cy, 71)) * s, v - (cy + noise(cx, cy, 72)) * s);
        if (d < d1) {
          d2 = d1;
          d1 = d;
          id = noise(cx, cy, 73);
        } else if (d < d2) d2 = d;
      }
    }
    let c = mix(hex('#3b1366'), hex('#c084fc'), id * 0.85 + 0.1);
    c = shade(c, 0.8 + 0.4 * (1 - d1 / s));
    return mix(c, hex('#f5edff'), smoothstep(0.09, 0, d2 - d1));
  }],
  ['hologram', N('Hologram', 'Hologramm', 'Hologramme', 'Holograma', 'Hologram', 'Голограмма'), 'legendary', (u, v) => {
    const hue = (u * 0.035 + v * 0.03 + fbm(u * 0.3, v * 0.3, 111) * 0.6) % 1;
    let c = hsl(hue, 0.7, 0.66);
    const shine = Math.max(0, 1 - Math.abs(u - v * 0.6 - 0.5) / 0.9);
    c = mix(c, C.white, shine * 0.45);
    if ((v * 2) % 1 < 0.12) c = shade(c, 0.88);
    return stars(c, u, v, 113, 2, 0.09, 0.2);
  }],
  ['sculk', N('Sculk', 'Sculk', 'Sculk', 'Sculk', 'Sculk', 'Скалк'), 'epic', (u, v) => {
    let c = mix(hex('#03141a'), hex('#0c2f38'), fbm(u * 0.55, v * 0.55, 81, 5));
    const ridge = Math.abs(fbm(u * 0.35, v * 0.35, 83, 4) - 0.5);
    c = mix(c, hex('#2de2f0'), smoothstep(0.045, 0, ridge));
    const gx = Math.floor(u / 2.5);
    const gy = Math.floor(v / 2.5);
    if (noise(gx, gy, 85) < 0.35) {
      const d = Math.hypot(u - (gx + 0.3 + noise(gx, gy, 86) * 0.4) * 2.5, v - (gy + 0.3 + noise(gx, gy, 87) * 0.4) * 2.5);
      c = glowAdd(c, hex('#59f6ff'), Math.exp(-d * 4) * 0.9);
    }
    return c;
  }],
  ['balance', N('Balance', 'Gleichgewicht', 'Équilibre', 'Equilibrio', 'Denge', 'Равновесие'), 'rare', (u, v) => {
    let c = mix(hex('#2b2f3a'), hex('#0e1016'), v / 16);
    const x = u - 5;
    const y = v - 8;
    const R = 3.6;
    const d = Math.hypot(x, y);
    if (d < R) {
      let white = x < 0;
      if (Math.hypot(x, y + R / 2) < R / 2) white = true;
      if (Math.hypot(x, y - R / 2) < R / 2) white = false;
      if (Math.hypot(x, y + R / 2) < R / 7) white = false;
      if (Math.hypot(x, y - R / 2) < R / 7) white = true;
      c = white ? hex('#f4f6fb') : hex('#111318');
    }
    if (Math.abs(d - R - 0.15) < 0.16) c = hex('#ffb627');
    return c;
  }],
  ['tiger', N('Tiger', 'Tiger', 'Tigre', 'Tigre', 'Kaplan', 'Тигр'), 'rare', (u, v) => {
    const side = Math.abs(u - 5) / 5;
    let c = mix(hex('#ffb14a'), hex('#e26b0a'), side);
    const s = Math.sin(v * 1.9 + fbm(u * 0.4, v * 0.4, 121) * 5 + side * 1.2);
    if (s > 0.62 - side * 0.5) c = hex('#1a0d05');
    if (v > 13.5) c = mix(c, hex('#fff4e3'), smoothstep(13.5, 15, v) * 0.85);
    return c;
  }],
  ['glitch', N('Glitch', 'Glitch', 'Glitch', 'Glitch', 'Glitch', 'Глитч'), 'epic', (u, v) => {
    const slice = Math.floor(v * 2);
    const shift = noise(slice, 0, 131) > 0.7 ? (noise(slice, 1, 132) - 0.5) * 3 : 0;
    const bars = ['#f4f6fb', '#facc15', '#22d3ee', '#22c55e', '#e879f9', '#ef4444', '#3b82f6'];
    const bar = (x) => (v > 11 ? (Math.floor(x / 1.25) % 2 ? hex('#111111') : hex('#e5e7eb')) : hex(bars[Math.max(0, Math.min(6, Math.floor((x / 10) * 7)))]));
    const x = u + shift;
    const c = [bar(x + 0.18)[0], bar(x)[1], bar(x - 0.18)[2], 255];
    const noisy = noise(Math.floor(u * 6), Math.floor(v * 6), 133) > 0.97;
    return noisy ? hex('#ffffff') : shade(c, (v * 3) % 1 < 0.15 ? 0.75 : 1);
  }],
];
for (const [id, names, rarity, design] of hdCapes) add('cape', id, names, rarity, capeTextureHD(design));

// ================================================================ HÜTE (attach: head)
function topHat(main, band) {
  return [{ pivot: [0, 0, 0], rotation: [-4, 0, 3], cubes: [
    box([-5.5, -9.5, -5.5], [11, 1, 11], P.solid(main)),
    box([-3.5, -16.5, -3.5], [7, 7, 7], P.vgrad(mix(main, C.white, 0.12), main, 2)),
    box([-3.5, -11.5, -3.5], [7, 2, 7], P.solid(band, 3), { inflate: 0.2 }),
  ] }];
}
addModel('hat', 'top_hat', N('Top Hat', 'Zylinder', 'Haut-de-forme', 'Sombrero de copa', 'Silindir Şapka', 'Цилиндр'), 'rare', 'head', topHat(C.black, C.gold));
addModel('hat', 'top_hat_white', N('White Top Hat', 'Weißer Zylinder', 'Haut-de-forme blanc', 'Sombrero de copa blanco', 'Beyaz Silindir Şapka', 'Белый цилиндр'), 'rare', 'head', topHat(hex('#e9edf5'), C.black));

/**
 * Krone: Reif mit dreieckigen Zacken, Perlen, Edelsteinen und Samtkissen.
 * Die Zacken sind um 45° gedrehte Quader, deren untere Hälfte im Reif steckt – so entstehen echte Dreiecke.
 */
function crown({ metal, dark, gemA, gemB, pearl, velvet }) {
  const light = mix(metal, C.white, 0.45);
  // Reif: oberste Reihe Glanzkante, Mitte Metall, unten dunkler Rand
  const band = (f, x, y, w, h) => {
    if (f === 'top') return grain(light, x, y, 0.06);
    if (f === 'bottom') return shade(dark, 0.8);
    const c = y === 0 ? light : y === h - 1 ? dark : metal;
    return grain(shade(c, lit(f)), x, y, 0.07, 5);
  };
  // Zacke: Mitte hell, Kanten satter – sieht nach der Drehung wie geschliffen aus
  const spike = (f, x, y, w, h) => {
    const edge = Math.max(Math.abs(x - (w - 1) / 2) / (w / 2), Math.abs(y - (h - 1) / 2) / (h / 2));
    return grain(shade(mix(light, metal, Math.min(1, edge * 1.2)), lit(f) + 0.05), x, y, 0.05, 9);
  };
  const gem = (c) => (f, x, y, w) => (x === 0 && y === 0 && w > 1 ? mix(c, C.white, 0.3) : shade(c, f === 'top' ? 1.2 : lit(f) + 0.1));
  const plush = (f, x, y) => grain(shade(velvet, f === 'top' ? 1.05 : 0.85), x, y, 0.18, 13);
  /** Erster Würfel bekommt die Textur, alle weiteren teilen sie. */
  const tpl = (size, paint, extra = {}) => {
    let first = null;
    return (from) => {
      const cube = first ? box(from, size, null, { ...extra, share: first }) : box(from, size, paint, extra);
      first ??= cube;
      return cube;
    };
  };

  const bandZ = tpl([10, 3, 1], band);
  const bandX = tpl([1, 3, 8], band);
  const spikes = {
    z: { true: tpl([3.2, 3.2, 0.9], spike), false: tpl([2.4, 2.4, 0.8], spike) },
    x: { true: tpl([0.9, 3.2, 3.2], spike), false: tpl([0.8, 2.4, 2.4], spike) },
  };
  const pearlCube = tpl([0.8, 0.8, 0.8], P.solid(pearl, 3, 0.04), { inflate: 0.05 });
  const bigGem = { z: tpl([2, 2, 1], gem(gemA)), x: tpl([1, 2, 2], gem(gemA)) };
  const smallGem = { z: tpl([1, 1, 1], gem(gemB)) };

  const parts = [{ pivot: [0, 0, 0], cubes: [
    bandZ([-5, -10.5, -5]), bandZ([-5, -10.5, 4]),
    bandX([-5, -10.5, -4]), bandX([4, -10.5, -4]),
    box([-3.9, -10.75, -3.9], [7.8, 2.5, 7.8], plush),
    box([-2.5, -11.75, -2.5], [5, 1, 5], plush),
  ] }];
  const gems = [];
  // je Seite: große Zacke in der Mitte, zwei kleine zu den Ecken hin; Perle auf jeder Spitze
  for (const [axis, at] of [['z', -4.5], ['z', 4.5], ['x', -4.5], ['x', 4.5]]) {
    const pos = (along, across = at) => (axis === 'z' ? [along, across] : [across, along]);
    for (const [along, big] of [[0, true], [-3.25, false], [3.25, false]]) {
      const [x, z] = pos(along);
      const s = big ? 3.2 : 2.4;
      const t = big ? 0.9 : 0.8; // unterschiedlich dick, damit sich überlappende Zacken nicht flimmern
      const from = axis === 'z' ? [-s / 2, -s / 2, -t / 2] : [-t / 2, -s / 2, -s / 2];
      parts.push({ pivot: [x, -10.5, z], rotation: axis === 'z' ? [0, 0, 45] : [45, 0, 0], cubes: [spikes[axis][big](from)] });
      const tip = -10.5 - s * Math.SQRT1_2;
      gems.push(pearlCube([x - 0.4, tip - 0.5, z - 0.4]));
    }
    // Edelsteine im Reif, stehen etwas vor
    const out = (depth) => (at < 0 ? at - 0.5 - depth : at + 0.5 + depth - 1);
    const [gx, gz] = pos(-1, out(0.4));
    gems.push(axis === 'z' ? bigGem.z([gx, -10, gz]) : bigGem.x([gx, -10, gz]));
    for (const along of [-3.5, 2.5]) {
      const [sx, sz] = pos(along, out(0.3));
      gems.push(smallGem.z([sx, -9.5, sz]));
    }
  }
  parts.push({ pivot: [0, 0, 0], emissive: true, cubes: gems });
  return parts;
}
addModel('hat', 'crown', N('Crown', 'Krone', 'Couronne', 'Corona', 'Taç', 'Корона'), 'legendary', 'head', crown({
  metal: C.gold, dark: C.goldDark, gemA: C.red, gemB: C.blue, pearl: hex('#fff7e6'), velvet: hex('#9f1239'),
}));
addModel('hat', 'crown_diamond', N('Diamond Crown', 'Diamantkrone', 'Couronne de diamant', 'Corona de diamante', 'Elmas Taç', 'Алмазная корона'), 'legendary', 'head', crown({
  metal: C.silver, dark: hex('#64748b'), gemA: C.cyan, gemB: hex('#a78bfa'), pearl: hex('#ecfeff'), velvet: hex('#1e3a8a'),
}));

function halo(c1, c2) {
  const bar = box([-4, 0, -4], [8, 1, 1], P.glow(c1, c2));
  const side = box([-4, 0, -3], [1, 1, 6], P.glow(c1, c2, 2));
  return [{ pivot: [0, -12, 0], emissive: true, animation: { type: 'bob', amplitude: 0.6, speed: 0.5 }, children: [
    { pivot: [0, 0, 0], emissive: true, animation: { type: 'spin', axis: 'y', speed: 0.15 }, cubes: [
      bar, box([-4, 0, 3], [8, 1, 1], null, { share: bar }), side, box([3, 0, -3], [1, 1, 6], null, { share: side }),
    ] },
  ] }];
}
addModel('hat', 'halo', N('Halo', 'Heiligenschein', 'Auréole', 'Aureola', 'Hale', 'Нимб'), 'epic', 'head', halo(hex('#fff7c2'), hex('#ffd54a')));
addModel('hat', 'halo_frost', N('Frost Halo', 'Frost-Heiligenschein', 'Auréole de givre', 'Aureola helada', 'Buz Halesi', 'Ледяной нимб'), 'epic', 'head', halo(hex('#e0f7ff'), hex('#5cc8ff')));

function wizard(main, accent, starColor, tipBend = 10) {
  const robe = (seed) => (f, x, y) => (noise(x, y, seed) > 0.9 && f !== 'bottom' ? starColor : grain(shade(mix(main, shade(main, 0.6), y / 6), lit(f)), x, y, 0.1, seed));
  return [{ pivot: [0, 0, 0], cubes: [box([-6, -9.5, -6], [12, 1, 12], robe(1))], children: [
    { pivot: [0, -9.5, 0], cubes: [box([-4, -3, -4], [8, 3, 8], (f, x, y) => (y === 2 && f !== 'top' && f !== 'bottom' ? shade(accent, lit(f)) : robe(2)(f, x, y)))], children: [
      { pivot: [0, -3, 0], rotation: [-6, 0, 4], cubes: [box([-3, -3, -3], [6, 3, 6], robe(3))], children: [
        { pivot: [0, -3, 0], rotation: [-8, 0, 6], cubes: [box([-2, -3, -2], [4, 3, 4], robe(4))], children: [
          { pivot: [0, -3, 0], rotation: [-tipBend, 0, tipBend], animation: { type: 'sway', axis: 'z', amplitude: 4, speed: 0.4 }, cubes: [box([-1, -3, -1], [2, 3, 2], robe(5))] },
        ] },
      ] },
    ] },
  ] }];
}
addModel('hat', 'wizard_hat', N('Wizard Hat', 'Zauberhut', 'Chapeau de sorcier', 'Sombrero de mago', 'Büyücü Şapkası', 'Шляпа волшебника'), 'epic', 'head', wizard(hex('#5b21b6'), C.gold, hex('#fde68a')));
addModel('hat', 'witch_hat', N('Witch Hat', 'Hexenhut', 'Chapeau de sorcière', 'Sombrero de bruja', 'Cadı Şapkası', 'Шляпа ведьмы'), 'rare', 'head', wizard(hex('#18181b'), hex('#a855f7'), hex('#a855f7'), 28));

/** Tierohren (Dreieck, innen andersfarbig) – zwei gespiegelte Teile mit gemeinsamer Textur. */
function ears(outer, inner, tall = 3) {
  const shape = box([-1.5, -tall, -0.5], [3, tall, 1], (f, x, y, w, h) => {
    const inTriangle = y === 0 ? x === 1 : true;
    if (f === 'top' || f === 'bottom') return null;
    if (!inTriangle && (f === 'north' || f === 'south')) return null;
    if (f === 'north' && x === 1 && y >= 1) return inner;
    if (tall > 3 && y === 0) return mix(outer, C.white, 0.7);
    return grain(shade(outer, lit(f)), x, y, 0.1);
  });
  return ['l', 'r'].map((side, i) => {
    const s = side === 'l' ? -1 : 1;
    return {
      pivot: [s * 2.5, -8, -1], rotation: [0, 0, s * 12],
      animation: { type: 'sway', axis: 'z', amplitude: s * 6, speed: 0.3 },
      cubes: [i === 0 ? shape : box([-1.5, -tall, -0.5], [3, tall, 1], null, { share: shape, mirror: true })],
    };
  });
}
addModel('hat', 'cat_ears', N('Cat Ears', 'Katzenohren', 'Oreilles de chat', 'Orejas de gato', 'Kedi Kulakları', 'Кошачьи ушки'), 'common', 'head', ears(hex('#24242c'), C.pink));
addModel('hat', 'fox_ears', N('Fox Ears', 'Fuchsohren', 'Oreilles de renard', 'Orejas de zorro', 'Tilki Kulakları', 'Лисьи ушки'), 'rare', 'head', ears(hex('#ea7a1f'), hex('#fff7ed'), 4));
addModel('hat', 'bunny_ears', N('Bunny Ears', 'Hasenohren', 'Oreilles de lapin', 'Orejas de conejo', 'Tavşan Kulakları', 'Заячьи ушки'), 'rare', 'head', ['l', 'r'].map((side) => {
  const s = side === 'l' ? -1 : 1;
  return {
    pivot: [s * 2, -8, 0], rotation: [-8, 0, s * 10], animation: { type: 'sway', axis: 'x', amplitude: 5, speed: 0.25, phase: side === 'l' ? 0 : 60 },
    cubes: [box([-1, -7, -0.5], [2, 7, 1], (f, x, y) => (f === 'north' && x === 0 && y > 0 && y < 6 ? hex('#fbcfe8') : P.solid(hex('#f8fafc'))(f, x, y)))],
  };
}));

addModel('hat', 'devil_horns', N('Devil Horns', 'Teufelshörner', 'Cornes de diable', 'Cuernos de diablo', 'Şeytan Boynuzları', 'Рога дьявола'), 'rare', 'head', (() => {
  const base = box([-1, -2, -1], [2, 2, 2], P.solid(hex('#b91c1c'), 1, 0.15));
  const tip = box([-0.5, -2, -0.5], [1, 2, 1], P.vgrad(hex('#1c1917'), hex('#7f1d1d')));
  return ['l', 'r'].map((side, i) => {
    const s = side === 'l' ? -1 : 1;
    return {
      pivot: [s * 2.5, -8, -1.5], rotation: [-10, 0, s * 25],
      cubes: [i === 0 ? base : box([-1, -2, -1], [2, 2, 2], null, { share: base })],
      children: [{ pivot: [0, -2, 0], rotation: [-15, 0, s * 25], cubes: [i === 0 ? tip : box([-0.5, -2, -0.5], [1, 2, 1], null, { share: tip })] }],
    };
  });
})());

addModel('hat', 'viking_helmet', N('Viking Helmet', 'Wikingerhelm', 'Casque viking', 'Casco vikingo', 'Viking Miğferi', 'Шлем викинга'), 'epic', 'head', (() => {
  const horn = box([-1, -4, -1], [2, 4, 2], P.vgrad(hex('#fff7ed'), hex('#d6c3a5')));
  const tip = box([-0.5, -3, -0.5], [1, 3, 1], P.solid(hex('#fffbeb')));
  return [
    { pivot: [0, 0, 0], cubes: [
      box([-4, -9, -4], [8, 3, 8], P.vgrad(hex('#e2e8f0'), hex('#94a3b8'), 3), { inflate: 0.6 }),
      box([-1, -9.6, -4.9], [2, 5, 1], P.solid(hex('#94a3b8'))),
    ] },
    ...['l', 'r'].map((side, i) => {
      const s = side === 'l' ? -1 : 1;
      return {
        pivot: [s * 4.6, -7.5, 0], rotation: [0, 0, s * 60],
        cubes: [i === 0 ? horn : box([-1, -4, -1], [2, 4, 2], null, { share: horn })],
        children: [{ pivot: [0, -4, 0], rotation: [0, 0, s * -45], cubes: [i === 0 ? tip : box([-0.5, -3, -0.5], [1, 3, 1], null, { share: tip })] }],
      };
    }),
  ];
})());

function beanie(main, stripe, pom) {
  return [{ pivot: [0, 0, 0], cubes: [
    box([-4, -9, -4], [8, 3, 8], (f, x, y) => grain(shade(y === 2 ? stripe : main, lit(f)), x, y, 0.15), { inflate: 0.65 }),
    box([-1.5, -11.5, -1.5], [3, 2, 3], P.solid(pom, 4, 0.2)),
  ] }];
}
addModel('hat', 'beanie_red', N('Red Beanie', 'Rote Mütze', 'Bonnet rouge', 'Gorro rojo', 'Kırmızı Bere', 'Красная шапка'), 'common', 'head', beanie(hex('#dc2626'), hex('#fef2f2'), hex('#fef2f2')));
addModel('hat', 'beanie_blue', N('Blue Beanie', 'Blaue Mütze', 'Bonnet bleu', 'Gorro azul', 'Mavi Bere', 'Синяя шапка'), 'common', 'head', beanie(C.blue, C.gold, C.gold));

addModel('hat', 'party_hat', N('Party Hat', 'Partyhut', 'Chapeau de fête', 'Gorro de fiesta', 'Parti Şapkası', 'Праздничный колпак'), 'common', 'head', (() => {
  const stripes = (seed) => (f, x, y) => grain(shade((x + y) % 4 < 2 ? hex('#ec4899') : hex('#22d3ee'), lit(f)), x, y, 0.08, seed);
  return [{ pivot: [1.5, -8.5, 0], rotation: [0, 0, 14], cubes: [box([-2.5, -2, -2.5], [5, 2, 5], stripes(1))], children: [
    { pivot: [0, -2, 0], cubes: [box([-1.5, -3, -1.5], [3, 3, 3], stripes(2))], children: [
      { pivot: [0, -3, 0], cubes: [box([-1, -2, -1], [2, 2, 2], P.solid(hex('#fde047'), 5, 0.2))] },
    ] },
  ] }];
})());

addModel('hat', 'santa_hat', N('Santa Hat', 'Weihnachtsmütze', 'Bonnet de Noël', 'Gorro de Papá Noel', 'Noel Baba Şapkası', 'Новогодний колпак'), 'rare', 'head', (() => {
  const red = (seed) => P.solid(hex('#dc2626'), seed, 0.12);
  return [{ pivot: [0, 0, 0], cubes: [box([-4, -9, -4], [8, 1, 8], P.solid(hex('#f8fafc'), 1, 0.15), { inflate: 0.7 })], children: [
    { pivot: [0, -9.5, 0], cubes: [box([-3.5, -3, -3.5], [7, 3, 7], red(2))], children: [
      { pivot: [0, -3, 0], rotation: [-30, 0, 0], cubes: [box([-2.5, -3, -2.5], [5, 3, 5], red(3))], children: [
        { pivot: [0, -3, 0], rotation: [-40, 0, 0], animation: { type: 'sway', axis: 'x', amplitude: 6, speed: 0.4 }, cubes: [box([-1.5, -3, -1.5], [3, 3, 3], red(4))], children: [
          { pivot: [0, -3, 0], cubes: [box([-1.5, -2, -1.5], [3, 2, 3], P.solid(hex('#f8fafc'), 6, 0.15))] },
        ] },
      ] },
    ] },
  ] }];
})());

// Headphones: band over the head, cups over the ears (accent = ring on the cup, emissive = glowing ring)
function headphones(band, cup, accent, { emissive = false, cushion = hex('#1a1a1f') } = {}) {
  const shell = (f, x, y) => {
    const ring = (f === 'west' || f === 'east') && x > 0 && x < 4 && y > 0 && y < 4;
    const center = (f === 'west' || f === 'east') && x > 1 && x < 3 && y > 1 && y < 3;
    if (center) return grain(shade(cup, 0.82), x, y, 0.06, 7);
    if (ring && !emissive) return grain(shade(accent, lit(f)), x, y, 0.06, 6);
    return grain(shade(cup, lit(f)), x, y, 0.08, 5);
  };
  const cupL = box([-6.2, -5.5, -2.5], [2, 5, 5], shell);
  const parts = [{ pivot: [0, 0, 0], cubes: [
    box([-4.5, -9.6, -1], [9, 1, 2], P.solid(band, 1, 0.08)),
    box([-5.5, -9.1, -1], [1, 3, 2], P.solid(band, 2, 0.08)),
    box([4.5, -9.1, -1], [1, 3, 2], P.solid(band, 3, 0.08)),
    box([-5.5, -6.6, -0.5], [1, 1.2, 1], P.solid(shade(band, 0.8), 4, 0.06)),
    box([4.5, -6.6, -0.5], [1, 1.2, 1], P.solid(shade(band, 0.8), 4, 0.06)),
    cupL,
    box([4.2, -5.5, -2.5], [2, 5, 5], null, { share: cupL }),
    box([-4.6, -5, -2], [0.6, 4, 4], P.solid(cushion, 8, 0.05)),
    box([4, -5, -2], [0.6, 4, 4], P.solid(cushion, 9, 0.05)),
  ] }];
  if (emissive) {
    parts.push({ pivot: [0, 0, 0], emissive: true, cubes: [
      box([-6.4, -4.7, -1.7], [0.2, 3.4, 3.4], P.glow(accent, mix(accent, C.white, 0.4), 3)),
      box([6.2, -4.7, -1.7], [0.2, 3.4, 3.4], P.glow(accent, mix(accent, C.white, 0.4), 4)),
    ] });
  }
  return parts;
}
addModel('hat', 'headphones', N('Studio Headphones', 'Studio-Kopfhörer', 'Casque studio', 'Auriculares de estudio', 'Stüdyo Kulaklığı', 'Студийные наушники'), 'rare', 'head', headphones(hex('#15171c'), hex('#22252d'), hex('#9aa3b5')));
addModel('hat', 'headphones_white', N('White Headphones', 'Weiße Kopfhörer', 'Casque blanc', 'Auriculares blancos', 'Beyaz Kulaklık', 'Белые наушники'), 'rare', 'head', headphones(hex('#e9ebf0'), hex('#f5f6f8'), hex('#c7ccd6'), { cushion: hex('#d5d9e1') }));
addModel('hat', 'headphones_moon', N('Moonvibe Headphones', 'Moonvibe-Kopfhörer', 'Casque Moonvibe', 'Auriculares Moonvibe', 'Moonvibe Kulaklığı', 'Наушники Moonvibe'), 'epic', 'head', headphones(hex('#1b1f3b'), hex('#2a2f5c'), hex('#8b93ff')));
addModel('hat', 'headphones_pink', N('Pink Headphones', 'Rosa Kopfhörer', 'Casque rose', 'Auriculares rosas', 'Pembe Kulaklık', 'Розовые наушники'), 'rare', 'head', headphones(hex('#f9a8d4'), hex('#fbcfe8'), hex('#ec4899'), { cushion: hex('#fdf2f8') }));
addModel('hat', 'headphones_gold', N('Gold Headphones', 'Goldene Kopfhörer', 'Casque doré', 'Auriculares dorados', 'Altın Kulaklık', 'Золотые наушники'), 'legendary', 'head', headphones(C.goldDark, C.gold, C.goldLight, { cushion: hex('#3b2a10') }));
addModel('hat', 'headphones_neon', N('Neon Headphones', 'Neon-Kopfhörer', 'Casque néon', 'Auriculares neón', 'Neon Kulaklık', 'Неоновые наушники'), 'legendary', 'head', headphones(hex('#0f1115'), hex('#181b22'), hex('#22d3ee'), { emissive: true }));

addModel('hat', 'propeller_cap', N('Propeller Cap', 'Propellermütze', 'Casquette à hélice', 'Gorra con hélice', 'Pervaneli Şapka', 'Кепка с пропеллером'), 'epic', 'head', [
  { pivot: [0, 0, 0], cubes: [
    box([-4, -9, -4], [8, 2, 8], (f, x, y) => grain(shade((Math.floor(x / 2) + (f === 'top' ? Math.floor(y / 2) : 0)) % 2 ? hex('#ef4444') : hex('#facc15'), lit(f)), x, y), { inflate: 0.6 }),
    box([-4, -7.4, -8.5], [8, 1, 4], P.solid(hex('#2563eb'))),
    box([-0.5, -11.5, -0.5], [1, 2, 1], P.solid(C.silver)),
  ] },
  { pivot: [0, -11.5, 0], animation: { type: 'spin', axis: 'y', speed: 2.2 }, cubes: [
    box([-4, -0.5, -0.5], [8, 1, 1], (f, x) => (x < 4 ? hex('#ef4444') : hex('#3b82f6'))),
  ] },
]);

addModel('hat', 'cowboy_hat', N('Cowboy Hat', 'Cowboyhut', 'Chapeau de cowboy', 'Sombrero vaquero', 'Kovboy Şapkası', 'Ковбойская шляпа'), 'rare', 'head', [
  { pivot: [0, 0, 0], rotation: [-5, 0, 0], cubes: [
    box([-7, -9.5, -6], [14, 1, 12], P.solid(hex('#92582d'), 1, 0.12)),
    box([-7, -10.5, -6], [1, 1, 12], P.solid(hex('#7a4a26'))),
    box([6, -10.5, -6], [1, 1, 12], P.solid(hex('#7a4a26'))),
    box([-4, -14.5, -4], [8, 5, 8], (f, x, y) => grain(shade(y === 4 ? hex('#3b2414') : hex('#a0643a'), lit(f)), x, y, 0.12)),
  ] },
]);

// ================================================================ BRILLEN & MASKEN (attach: head, Gesicht bei z=-4)
const temples = (color) => [box([-5, -5, -4.5], [1, 1, 5], P.solid(color)), box([4, -5, -4.5], [1, 1, 5], P.solid(color))];
function glasses(lensPaint, frame, lensW = 3, lensH = 2, top = -5) {
  const lens = box([-4, top, -5], [lensW, lensH, 1], lensPaint);
  return [{ pivot: [0, 0, 0], cubes: [
    lens,
    box([4 - lensW, top, -5], [lensW, lensH, 1], null, { share: lens, mirror: true }),
    box([-4 + lensW, -5, -5], [8 - 2 * lensW, 1, 1], P.solid(frame)),
    ...temples(frame),
  ] }];
}
addModel('face', 'sunglasses', N('Sunglasses', 'Sonnenbrille', 'Lunettes de soleil', 'Gafas de sol', 'Güneş Gözlüğü', 'Солнцезащитные очки'), 'common', 'head',
  glasses((f, x, y) => (f === 'north' && x + y <= 1 ? hex('#bae6fd') : f === 'north' ? mix(hex('#334155'), hex('#0f172a'), y) : P.solid(hex('#1e293b'))(f, x, y)), hex('#cbd5e1')));
addModel('face', 'pixel_shades', N('Pixel Shades', 'Pixel-Brille', 'Lunettes pixel', 'Gafas pixeladas', 'Piksel Gözlük', 'Пиксельные очки'), 'epic', 'head', [{ pivot: [0, 0, 0], cubes: [
  box([-4, -5, -5], [8, 2, 1], (f, x, y) => {
    if (f !== 'north' && f !== 'south') return hex('#000000');
    if (y === 1 && (x === 3 || x === 4)) return null; // Lücke über der Nase
    if (y === 1 && (x === 1 || x === 6)) return hex('#ffffff'); // Glanz-Pixel
    return hex('#000000');
  }),
  ...temples(hex('#000000')),
] }]);
addModel('face', 'heart_glasses', N('Heart Glasses', 'Herz-Brille', 'Lunettes cœur', 'Gafas de corazón', 'Kalp Gözlük', 'Очки-сердечки'), 'rare', 'head',
  glasses((f, x, y) => {
    const heart = ['101', '111', '010'][y]?.[x] === '1';
    if (!heart && (f === 'north' || f === 'south')) return null;
    return shade(hex('#ec4899'), f === 'north' ? 1 : 0.8);
  }, hex('#be185d'), 3, 3, -6));
addModel('face', 'nerd_glasses', N('Nerd Glasses', 'Nerd-Brille', 'Lunettes de geek', 'Gafas de nerd', 'İnek Gözlüğü', 'Очки ботаника'), 'common', 'head',
  glasses((f, x, y, w, h) => ((f === 'north' || f === 'south') && x > 0 && x < w - 1 && y > 0 && y < h - 1 ? null : hex('#111111')), hex('#111111'), 3, 3, -6));
addModel('face', 'glasses_3d', N('3D Glasses', '3D-Brille', 'Lunettes 3D', 'Gafas 3D', '3D Gözlük', '3D-очки'), 'common', 'head', [{ pivot: [0, 0, 0], cubes: [
  box([-4, -5, -5], [3, 2, 1], P.solid(hex('#ef4444'))),
  box([1, -5, -5], [3, 2, 1], P.solid(hex('#22d3ee'))),
  box([-4.5, -5.5, -5.2], [9, 1, 1], P.solid(hex('#f8fafc'))),
  box([-1, -5, -5], [2, 1, 1], P.solid(hex('#f8fafc'))),
  ...temples(hex('#f8fafc')),
] }]);
addModel('face', 'monocle', N('Monocle', 'Monokel', 'Monocle', 'Monóculo', 'Monokl', 'Монокль'), 'rare', 'head', [{ pivot: [0, 0, 0], cubes: [
  box([0.5, -6, -5], [3, 3, 1], (f, x, y) => ((f === 'north' || f === 'south') && x === 1 && y === 1 ? null : shade(C.gold, lit(f)))),
  box([3, -3, -4.7], [1, 4, 1], P.solid(C.goldLight)),
] }]);
addModel('face', 'mustache', N('Mustache', 'Schnurrbart', 'Moustache', 'Bigote', 'Bıyık', 'Усы'), 'common', 'head', [{ pivot: [0, 0, 0], cubes: [
  box([-3, -3, -5], [6, 1, 1], P.solid(hex('#3b2414'))),
  box([-4, -4, -5], [1, 1, 1], P.solid(hex('#3b2414'))),
  box([3, -4, -5], [1, 1, 1], P.solid(hex('#3b2414'))),
] }]);
addModel('face', 'ninja_mask', N('Ninja Mask', 'Ninja-Maske', 'Masque de ninja', 'Máscara ninja', 'Ninja Maskesi', 'Маска ниндзя'), 'rare', 'head', [{ pivot: [0, 0, 0], cubes: [
  box([-4, -4, -4], [8, 4, 8], P.solid(hex('#1f2937'), 2, 0.15), { inflate: 0.6 }),
  box([-4, -8, -4], [8, 1, 8], (f, x, y) => (f === 'top' || f === 'bottom' ? null : P.solid(hex('#dc2626'))(f, x, y)), { inflate: 0.62 }),
  box([-1, -4.5, 4.4], [2, 2, 1], P.solid(hex('#111827'))),
], children: [
  { pivot: [0, -3, 5], animation: { type: 'sway', axis: 'x', amplitude: 8, speed: 0.5 }, cubes: [box([-1, 0, 0], [1, 4, 1], P.solid(hex('#111827'))), box([0.2, 0, 0.3], [1, 3, 1], P.solid(hex('#1f2937')))] },
] }]);

// ================================================================ RÜCKEN (attach: body, Rücken bei z=+2)
function backpack(main, accent) {
  const strapColor = shade(main, 0.7);
  return [{ pivot: [0, 0, 0], cubes: [
    box([-3, 1, 2.4], [6, 8, 3], (f, x, y) => grain(shade(y === 0 ? accent : main, lit(f)), x, y, 0.1)),
    box([-2, 5, 5.4], [4, 3, 1], P.solid(accent, 3)),
    box([-3, -0.4, -2.5], [1, 1, 5], P.solid(strapColor)),
    box([2, -0.4, -2.5], [1, 1, 5], P.solid(strapColor)),
    box([-3, 0, -2.7], [1, 6, 1], P.solid(strapColor)),
    box([2, 0, -2.7], [1, 6, 1], P.solid(strapColor)),
  ] }];
}
addModel('back', 'backpack_blue', N('Blue Backpack', 'Blauer Rucksack', 'Sac à dos bleu', 'Mochila azul', 'Mavi Sırt Çantası', 'Синий рюкзак'), 'common', 'body', backpack(C.blue, hex('#1e3a8a')));
addModel('back', 'backpack_red', N('Red Backpack', 'Roter Rucksack', 'Sac à dos rouge', 'Mochila roja', 'Kırmızı Sırt Çantası', 'Красный рюкзак'), 'common', 'body', backpack(hex('#dc2626'), hex('#7f1d1d')));
addModel('back', 'moon_backpack', N('Moonvibe Backpack', 'Moonvibe-Rucksack', 'Sac à dos Moonvibe', 'Mochila Moonvibe', 'Moonvibe Sırt Çantası', 'Рюкзак Moonvibe'), 'epic', 'body', backpack(C.gold, C.blue));

function sword(blade, guard, grip, angle) {
  return [{ pivot: [0, 6, 2.6], rotation: [0, 0, angle], cubes: [
    box([-1, -12, 0], [2, 11, 1], P.vgrad(mix(blade, C.white, 0.5), blade, 2)),
    box([-2.5, -1, -0.5], [5, 1, 2], P.solid(guard)),
    box([-0.5, 0, 0], [1, 3, 1], P.solid(grip)),
    box([-1, 3, -0.2], [2, 1, 1], P.solid(guard)),
  ] }];
}
addModel('back', 'crystal_sword', N('Crystal Sword', 'Kristallschwert', 'Épée de cristal', 'Espada de cristal', 'Kristal Kılıç', 'Кристальный меч'), 'epic', 'body', sword(hex('#22d3ee'), C.gold, C.brown, 40));
addModel('back', 'katana', N('Katana', 'Katana', 'Katana', 'Katana', 'Katana', 'Катана'), 'epic', 'body', sword(hex('#cbd5e1'), hex('#111111'), hex('#7f1d1d'), -38));
addModel('back', 'quiver', N('Quiver', 'Köcher', 'Carquois', 'Carcaj', 'Okluk', 'Колчан'), 'rare', 'body', [{ pivot: [0, 6, 3], rotation: [0, 0, 25], cubes: [
  box([-1.5, -5, -0.5], [3, 9, 3], (f, x, y) => grain(shade(y === 1 || y === 7 ? C.goldDark : hex('#7a4a26'), lit(f)), x, y, 0.12)),
  box([-1, -8, 0], [1, 3, 1], (f, x, y) => (y === 0 ? hex('#f8fafc') : hex('#a16207'))),
  box([0.3, -7.5, 1], [1, 3, 1], (f, x, y) => (y === 0 ? hex('#ef4444') : hex('#a16207'))),
] }]);
addModel('back', 'jetpack', N('Jetpack', 'Jetpack', 'Jetpack', 'Jetpack', 'Jetpack', 'Реактивный ранец'), 'legendary', 'body', (() => {
  const tank = box([-3.5, 1, 2.4], [3, 7, 3], (f, x, y) => grain(shade(y === 2 ? C.blue : hex('#9ca3af'), lit(f)), x, y, 0.1));
  const nozzle = box([-3, 8, 2.9], [2, 1, 2], P.solid(hex('#374151')));
  const flame = box([-2.5, 0, -0.5], [1, 2, 1], P.vgrad(hex('#fde047'), hex('#f97316')));
  return [
    { pivot: [0, 0, 0], cubes: [tank, box([0.5, 1, 2.4], [3, 7, 3], null, { share: tank }), nozzle, box([1, 8, 2.9], [2, 1, 2], null, { share: nozzle })] },
    { pivot: [0, 9, 3.9], emissive: true, animation: { type: 'bob', amplitude: 0.4, speed: 3 }, cubes: [flame, box([1.5, 0, -0.5], [1, 2, 1], null, { share: flame })] },
  ];
})());
addModel('back', 'moon_shield', N('Moonvibe Shield', 'Moonvibe-Schild', 'Bouclier Moonvibe', 'Escudo Moonvibe', 'Moonvibe Kalkanı', 'Щит Moonvibe'), 'epic', 'body', [{ pivot: [0, 0, 0], cubes: [
  box([-4, 1, 2.4], [8, 9, 1], (f, x, y, w, h) => {
    if (f !== 'south' && f !== 'north') return shade(C.gold, 0.8);
    if (x === 0 || x === w - 1 || y === 0 || y === h - 1) return C.gold;
    // the outer side (south) shows the moon
    const m = f === 'south' ? moonPixel(x, y, 4, 4.5, 2.6) : null;
    if (m) return m;
    return grain(mix(MOON.navy, MOON.night, y / h), x, y);
  }),
] }]);

// ================================================================ FLÜGEL (attach: body)
const INNER = { w: 12, h: 20, top: -9 };
const OUTER = { w: 10, h: 20, top: -11 };

function wings(shape, { spread = 28, flap = 16, emissive = false, scale = 1 } = {}) {
  const inner = { w: Math.round(INNER.w * scale), h: Math.round(INNER.h * scale), top: Math.round(INNER.top * scale) };
  const outer = { w: Math.round(OUTER.w * scale), h: Math.round(OUTER.h * scale), top: Math.round(OUTER.top * scale) };
  const segment = (seg, offset) => (f, x, y, w) => {
    if (f !== 'north' && f !== 'south') return null; // Kanten unsichtbar → flacher Flügel
    const ox = (offset + (f === 'north' ? x : w - 1 - x)) / scale;
    const c = shape(ox, (seg.top + y) / scale);
    return c ? shade(c, f === 'north' ? 0.85 : 1) : null;
  };
  const innerCube = box([0, inner.top, 0], [inner.w, inner.h, 1], segment(inner, 0));
  const outerCube = box([0, outer.top, 0], [outer.w, outer.h, 1], segment(outer, inner.w));
  const wing = (side) => {
    const s = side === 'l' ? 1 : -1;
    const left = side === 'l';
    return {
      pivot: [s * 2.5, 4, 2.5], rotation: [10, -s * spread, -s * 20], emissive,
      animation: { type: 'flap', axis: 'y', amplitude: -s * flap, speed: 0.55 },
      cubes: [left ? innerCube : box([-inner.w, inner.top, 0], [inner.w, inner.h, 1], null, { share: innerCube, mirror: true })],
      children: [{
        pivot: [s * inner.w, 0, 0], rotation: [0, -s * 10, 0], emissive,
        animation: { type: 'flap', axis: 'y', amplitude: -s * flap * 0.7, speed: 0.55, phase: -50 },
        cubes: [left ? outerCube : box([-outer.w, outer.top, 0], [outer.w, outer.h, 1], null, { share: outerCube, mirror: true })],
      }],
    };
  };
  return [wing('l'), wing('r')];
}

/** Federflügel: Bogen oben, gezackte Federspitzen unten. */
function feathers(top, bottom, tip, edge) {
  return (ox, gy) => {
    const W = 22;
    const yTop = 1 - 7 * Math.sin((Math.PI * Math.min(ox, W)) / 25);
    const yBottom = 9 - ox * 0.42 - (Math.floor(ox) % 3 === 2 ? 1.5 : 0);
    if (gy < yTop || gy > yBottom || ox >= W) return null;
    const t = (gy - yTop) / Math.max(1, yBottom - yTop);
    let c = mix(top, bottom, t);
    if (gy - yTop < 1.2) c = edge;
    else if (t > 0.55 && Math.floor(ox) % 3 === 0) c = shade(c, 0.86);
    else if (t <= 0.55 && (Math.floor(gy) + Math.floor(ox)) % 4 === 0) c = shade(c, 0.93);
    if (yBottom - gy < 2.2) c = mix(c, tip, 0.6);
    return grain(c, ox, gy, 0.06, 4);
  };
}
/** Hautflügel (Drache, Dämon, Fledermaus): Knochen + Bögen. */
function membrane(top, bottom, bone) {
  return (ox, gy) => {
    const W = 22;
    const yTop = -3 - ox * 0.38;
    const yBottom = 8 - ox * 0.3 - 2.2 * Math.abs(Math.sin((ox * Math.PI) / 5.5));
    if (ox >= W || gy < yTop - 1 || gy > yBottom) return null;
    if (gy < yTop + 0.6) return grain(bone, ox, gy, 0.1);
    for (const finger of [7, 14, 20]) {
      if (ox <= finger && ox >= finger - 4 && Math.abs(gy - (yTop + (finger - ox) * 2.4)) < 0.8) return grain(bone, ox, gy, 0.1);
    }
    return grain(mix(top, bottom, (gy - yTop) / Math.max(1, yBottom - yTop)), ox, gy, 0.1, 7);
  };
}
/** Insektenflügel (Schmetterling, Fee). */
function lobes(c1, c2, outline, spot) {
  return (ox, gy) => {
    const upper = ((ox - 10) / 11) ** 2 + ((gy + 4) / 6.5) ** 2;
    const lower = ((ox - 7) / 7.5) ** 2 + ((gy - 5) / 4.5) ** 2;
    const d = Math.min(upper, lower);
    if (d > 1) return null;
    if (d > 0.78) return outline;
    if ((ox - 13) ** 2 + (gy + 5) ** 2 < 4 || (ox - 7) ** 2 + (gy - 5) ** 2 < 2.5) return spot;
    return grain(mix(c1, c2, ox / 21), ox, gy, 0.08, 11);
  };
}
/** Mecha-Flügel: gestufte Metallplatten mit Leuchtlinien. */
function plates(metal, light) {
  return (ox, gy) => {
    const W = 22;
    const step = Math.floor(ox / 4);
    const yTop = -6 - step * 1.5;
    const yBottom = 6 - step * 1.2;
    if (ox >= W || gy < yTop || gy > yBottom) return null;
    if (Math.floor(ox) % 4 === 0 || Math.abs(gy - yBottom) < 0.6) return light;
    return grain(mix(metal, shade(metal, 0.7), (gy - yTop) / (yBottom - yTop)), ox, gy, 0.08);
  };
}

const wingList = [
  ['angel_wings', N('Angel Wings', 'Engelsflügel', "Ailes d'ange", 'Alas de ángel', 'Melek Kanatları', 'Крылья ангела'), 'epic', feathers(hex('#ffffff'), hex('#dbe7ff'), hex('#b8cdf5'), hex('#ffffff')), { spread: 30, flap: 14 }],
  ['moon_wings', N('Moonvibe Wings', 'Moonvibe-Flügel', 'Ailes Moonvibe', 'Alas Moonvibe', 'Moonvibe Kanatları', 'Крылья Moonvibe'), 'legendary', feathers(hex('#5cc8ff'), hex('#1f6fe0'), C.gold, hex('#bfe9ff')), {}],
  ['dragon_wings', N('Dragon Wings', 'Drachenflügel', 'Ailes de dragon', 'Alas de dragón', 'Ejderha Kanatları', 'Крылья дракона'), 'legendary', membrane(hex('#b91c1c'), hex('#4c0519'), hex('#2a1b1b')), { spread: 26, flap: 12 }],
  ['demon_wings', N('Demon Wings', 'Dämonenflügel', 'Ailes de démon', 'Alas de demonio', 'İblis Kanatları', 'Крылья демона'), 'epic', membrane(hex('#27272a'), hex('#09090b'), hex('#b91c1c')), { spread: 26, flap: 12 }],
  ['bat_wings', N('Bat Wings', 'Fledermausflügel', 'Ailes de chauve-souris', 'Alas de murciélago', 'Yarasa Kanatları', 'Крылья летучей мыши'), 'rare', membrane(hex('#44403c'), hex('#1c1917'), hex('#0c0a09')), { spread: 24, flap: 22, scale: 0.7 }],
  ['butterfly_wings', N('Butterfly Wings', 'Schmetterlingsflügel', 'Ailes de papillon', 'Alas de mariposa', 'Kelebek Kanatları', 'Крылья бабочки'), 'rare', lobes(hex('#c084fc'), hex('#22d3ee'), hex('#1e1b4b'), hex('#fef3c7')), { spread: 22, flap: 20 }],
  ['fairy_wings', N('Fairy Wings', 'Feenflügel', 'Ailes de fée', 'Alas de hada', 'Peri Kanatları', 'Крылья феи'), 'epic', lobes(hex('#fbcfe8'), hex('#e0f2fe'), hex('#f9a8d4'), hex('#ffffff')), { spread: 22, flap: 24, emissive: true, scale: 0.85 }],
  ['phoenix_wings', N('Phoenix Wings', 'Phönixflügel', 'Ailes de phénix', 'Alas de fénix', 'Anka Kanatları', 'Крылья феникса'), 'legendary', feathers(hex('#fde047'), hex('#f97316'), hex('#dc2626'), hex('#fef9c3')), { emissive: true, flap: 18 }],
  ['frost_wings', N('Frost Wings', 'Frostflügel', 'Ailes de givre', 'Alas de escarcha', 'Buz Kanatları', 'Ледяные крылья'), 'legendary', feathers(hex('#f0f9ff'), hex('#7dd3fc'), hex('#38bdf8'), hex('#ffffff')), { emissive: true, flap: 12 }],
  ['mecha_wings', N('Mecha Wings', 'Mecha-Flügel', 'Ailes mécaniques', 'Alas mecánicas', 'Mekanik Kanatlar', 'Механические крылья'), 'epic', plates(hex('#94a3b8'), hex('#22d3ee')), { spread: 32, flap: 6 }],
];
for (const [id, names, rarity, shape, options] of wingList) addModel('wings', id, names, rarity, 'body', wings(shape, options));

// ================================================================ SHOES (attach: legs, drawn on both legs)
// Leg space: leg from y=0 (hip) to y=12 (ground), x -2..2, z -2..2, front (toes) at -z.
function sneaker({ upper, sole, accent, laces = C.white, toe = null, stripe = null, emissive = false }) {
  const upperPaint = (f, x, y, w, h) => {
    if (f === 'north' && x > 0 && x < w - 1 && y < h - 1 && (y % 2 === 0)) return grain(shade(laces, lit(f)), x, y, 0.05, 3);
    if (stripe && (f === 'west' || f === 'east') && y === h - 2) return grain(shade(stripe, lit(f)), x, y, 0.05, 4);
    if ((f === 'west' || f === 'east') && x >= w - 2 && y < 2) return grain(shade(accent, lit(f)), x, y, 0.05, 5);
    return grain(shade(upper, lit(f)), x, y, 0.07, 2);
  };
  const solePaint = (f, x, y) => grain(shade(f === 'top' ? upper : sole, lit(f)), x, y, 0.05, 6);
  const toePaint = (f, x, y) => grain(shade(toe ?? upper, lit(f)), x, y, 0.06, 7);
  const parts = [{ pivot: [0, 0, 0], cubes: [
    box([-2.3, 9.2, -2.3], [4.6, 2, 4.6], upperPaint),
    box([-2.3, 10.4, -3.6], [4.6, 1, 1.4], toePaint),
    box([-2.5, 11.2, -3.8], [5, 1, 6.3], solePaint),
    box([-2.4, 8.6, 1.6], [4.8, 1.2, 0.9], P.solid(accent, 8, 0.06)),
  ] }];
  if (emissive) {
    parts.push({ pivot: [0, 0, 0], emissive: true, cubes: [
      box([-2.55, 11.65, -3.85], [5.1, 0.3, 6.4], P.glow(accent, mix(accent, C.white, 0.35), 9)),
    ] });
  }
  return parts;
}
const shoeList = [
  ['sneakers_classic', N('Classic Sneakers', 'Klassische Sneaker', 'Baskets classiques', 'Zapatillas clásicas', 'Klasik Spor Ayakkabı', 'Классические кеды'), 'common', { upper: hex('#f4f5f7'), sole: hex('#e3e5ea'), accent: hex('#c9ced8'), laces: hex('#ffffff') }],
  ['sneakers_black', N('Black Sneakers', 'Schwarze Sneaker', 'Baskets noires', 'Zapatillas negras', 'Siyah Spor Ayakkabı', 'Чёрные кроссовки'), 'common', { upper: hex('#1d1f25'), sole: hex('#f4f5f7'), accent: hex('#3a3e48'), laces: hex('#e5e7eb') }],
  ['sneakers_red', N('Red Runners', 'Rote Runner', 'Runners rouges', 'Corredoras rojas', 'Kırmızı Koşu Ayakkabısı', 'Красные беговые'), 'rare', { upper: hex('#dc2626'), sole: hex('#f8fafc'), accent: hex('#111827'), stripe: hex('#ffffff') }],
  ['sneakers_moon', N('Moonwalkers', 'Moonwalker', 'Moonwalkers', 'Moonwalkers', 'Ay Yürüyüşçüleri', 'Лунные кроссовки'), 'epic', { upper: hex('#232850'), sole: hex('#e9e6dc'), accent: hex('#8b93ff'), laces: hex('#c7cbff'), toe: hex('#e9e6dc') }],
  ['sneakers_court', N('Court High-Tops', 'Court-High-Tops', 'Montantes court', 'Botas de cancha', 'Basketbol Botu', 'Баскетбольные кеды'), 'rare', { upper: hex('#f4f5f7'), sole: hex('#f4f5f7'), accent: hex('#2563eb'), stripe: hex('#2563eb'), toe: hex('#2563eb') }],
  ['sneakers_gold', N('Gold Sneakers', 'Goldene Sneaker', 'Baskets dorées', 'Zapatillas doradas', 'Altın Spor Ayakkabı', 'Золотые кроссовки'), 'legendary', { upper: C.gold, sole: hex('#fff7e0'), accent: C.goldDark, laces: hex('#fff7e0') }],
  ['sneakers_neon', N('Neon Runners', 'Neon-Runner', 'Runners néon', 'Corredoras neón', 'Neon Koşu Ayakkabısı', 'Неоновые кроссовки'), 'legendary', { upper: hex('#111318'), sole: hex('#1c1f27'), accent: hex('#a3e635'), laces: hex('#a3e635'), emissive: true }],
];
for (const [id, names, rarity, opts] of shoeList) addModel('shoes', id, names, rarity, 'legs', sneaker(opts));

// ================================================================ BEGLEITER / PETS (attach: body, schweben neben dem Kopf)
function pet(cubes, extraChildren = [], sway = 0.08) {
  return [{
    pivot: [9, -6, 1], animation: { type: 'bob', amplitude: 1.2, speed: 0.35 },
    children: [{ pivot: [0, 0, 0], animation: { type: 'sway', axis: 'y', amplitude: 25, speed: sway }, cubes, children: extraChildren }],
  }];
}
/** Gesicht auf der Vorderseite (Augen + optional Mund). */
const face = (base, eye = hex('#111111'), mouth = null) => (f, x, y, w, h) => {
  if (f === 'north') {
    if (y === Math.floor(h / 2) - 1 && (x === 1 || x === w - 2)) return eye;
    if (mouth && y === Math.floor(h / 2) + 1 && x > 1 && x < w - 2) return mouth;
  }
  return base(f, x, y, w, h);
};

addModel('pet', 'moon_buddy', N('Moonvibe Buddy', 'Moonvibe-Kumpel', 'Copain Moonvibe', 'Compañero Moonvibe', 'Moonvibe Dostu', 'Приятель Moonvibe'), 'legendary', 'body', pet([
  box([-2.5, -2.5, -2.5], [5, 5, 5], face((f, x, y) => {
    if (f === 'top') return grain(hex('#4ade80'), x, y, 0.15);
    if (f === 'bottom') return grain(hex('#7a4a26'), x, y, 0.15);
    return grain(y === 0 ? hex('#4ade80') : shade(hex('#9a6534'), lit(f)), x, y, 0.15);
  }, hex('#0b3f8f'), hex('#f472b6'))),
]));
addModel('pet', 'ghost', N('Ghost', 'Geist', 'Fantôme', 'Fantasma', 'Hayalet', 'Призрак'), 'rare', 'body', pet([
  box([-2.5, -3, -2.5], [5, 6, 5], face((f, x, y, w, h) => {
    if ((f === 'north' || f === 'south' || f === 'west' || f === 'east') && y === h - 1 && x % 2 === 1) return null;
    return shade(hex('#f1f5f9'), lit(f));
  })),
], [], 0.12));
addModel('pet', 'slime', N('Slime', 'Schleim', 'Slime', 'Slime', 'Balçık', 'Слизень'), 'common', 'body', pet([
  box([-2.5, -2.5, -2.5], [5, 5, 5], face(P.solid(hex('#86efac'), 2, 0.15), hex('#14532d'), hex('#14532d'))),
]));
addModel('pet', 'fire_spirit', N('Fire Spirit', 'Feuergeist', 'Esprit de feu', 'Espíritu de fuego', 'Ateş Ruhu', 'Дух огня'), 'epic', 'body', [{
  pivot: [9, -6, 1], emissive: true, animation: { type: 'bob', amplitude: 1.4, speed: 0.5 },
  cubes: [box([-2, -2, -2], [4, 4, 4], face(P.glow(hex('#f97316'), hex('#fde047'), 3), hex('#7c2d12')))],
  children: [{ pivot: [0, -2, 0], emissive: true, animation: { type: 'sway', axis: 'z', amplitude: 12, speed: 1.2 }, cubes: [box([-1, -2, -1], [2, 2, 2], P.glow(hex('#fde047'), hex('#fff7c2'), 5))] }],
}]);
addModel('pet', 'cloud', N('Cloud', 'Wolke', 'Nuage', 'Nube', 'Bulut', 'Облако'), 'common', 'body', pet([
  box([-3.5, -1, -2], [7, 3, 4], P.solid(hex('#f8fafc'), 1, 0.06)),
  box([-2, -2.5, -1.5], [3, 2, 3], P.solid(hex('#ffffff'), 2, 0.06)),
  box([0.5, -2, -1.5], [2, 1, 3], P.solid(hex('#ffffff'), 3, 0.06)),
], [], 0.05));
addModel('pet', 'bee', N('Bee', 'Biene', 'Abeille', 'Abeja', 'Arı', 'Пчела'), 'rare', 'body', pet([
  box([-2, -2, -2.5], [4, 4, 5], face((f, x) => {
    const stripe = f === 'west' || f === 'east' || f === 'top' || f === 'bottom' ? Math.floor(x) % 2 === 1 : false;
    return shade(stripe ? hex('#111111') : hex('#facc15'), lit(f));
  })),
], [
  { pivot: [-1, -2, 0], animation: { type: 'flap', axis: 'z', amplitude: 35, speed: 4 }, cubes: [box([-3, -0.5, -1], [3, 1, 2], P.solid(hex('#e0f2fe')))] },
  { pivot: [1, -2, 0], animation: { type: 'flap', axis: 'z', amplitude: -35, speed: 4 }, cubes: [box([0, -0.5, -1], [3, 1, 2], P.solid(hex('#e0f2fe')))] },
]));
addModel('pet', 'ufo', N('Mini UFO', 'Mini-UFO', 'Mini OVNI', 'Mini OVNI', 'Mini UFO', 'Мини-НЛО'), 'epic', 'body', [{
  pivot: [9, -7, 1], animation: { type: 'bob', amplitude: 1, speed: 0.6 },
  children: [{ pivot: [0, 0, 0], animation: { type: 'spin', axis: 'y', speed: 0.4 }, cubes: [
    box([-3.5, 0, -3.5], [7, 1, 7], P.solid(hex('#94a3b8'), 1, 0.1)),
    box([-1.5, -2, -1.5], [3, 2, 3], P.glow(hex('#a5f3fc'), hex('#67e8f9'))),
  ], children: [{ pivot: [0, 0, 0], emissive: true, cubes: [
    box([-3, 1, -0.5], [1, 1, 1], P.solid(hex('#22c55e'))),
    box([2, 1, -0.5], [1, 1, 1], P.solid(hex('#ef4444'))),
    box([-0.5, 1, -3], [1, 1, 1], P.solid(hex('#facc15'))),
    box([-0.5, 1, 2], [1, 1, 1], P.solid(hex('#3b82f6'))),
  ] }] }],
}]);

// ================================================================ Katalog
writeFileSync(join(ROOT, 'cosmetics.json'), JSON.stringify(catalog, null, 2) + '\n');
const count = (type) => catalog.filter((c) => c.type === type).length;
console.log(`${catalog.length} Cosmetics: ${Object.keys(FOLDERS).map((t) => `${count(t)} ${t}`).join(', ')}`);
