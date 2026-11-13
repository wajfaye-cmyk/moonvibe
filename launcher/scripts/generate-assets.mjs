// Erzeugt Logo/Icons und die Cape-Texturen als PNG (ohne externe Bibliotheken).
// Aufruf: node scripts/generate-assets.mjs
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { crc32, deflateSync } from 'node:zlib';

const repo = join(dirname(fileURLToPath(import.meta.url)), '..', '..');

// ---------- PNG schreiben ----------
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body) >>> 0);
  return Buffer.concat([len, body, crc]);
}

function png(width, height, pixel) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0; // Filter: none
    for (let x = 0; x < width; x++) {
      const [r, g, b, a = 255] = pixel(x, y);
      const i = y * (width * 4 + 1) + 1 + x * 4;
      raw[i] = clamp(r);
      raw[i + 1] = clamp(g);
      raw[i + 2] = clamp(b);
      raw[i + 3] = clamp(a);
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // Bit-Tiefe
  ihdr[9] = 6; // RGBA
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];

function save(path, data) {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, data);
  console.log('geschrieben:', path.replace(repo, '.'));
}

// ---------- Logo "mv" ----------
// 5x7-Pixelschrift
const GLYPHS = {
  e: ['.....', '.....', '.###.', '#...#', '#####', '#....', '.####'],
  z: ['.....', '.....', '#####', '...#.', '..#..', '.#...', '#####'],
};

function textMask(text, scale) {
  const w = text.length * 6 - 1;
  return {
    width: w * scale,
    height: 7 * scale,
    at(x, y) {
      const gx = Math.floor(x / scale);
      const gy = Math.floor(y / scale);
      const ch = text[Math.floor(gx / 6)];
      const col = gx % 6;
      if (!ch || col === 5) return false;
      return GLYPHS[ch][gy]?.[col] === '#';
    },
  };
}

const VIOLET = hex('#8b5cf6');
const CYAN = hex('#22d3ee');
const DARK = hex('#0b0b14');

function icon(size) {
  const radius = size * 0.22;
  const scale = Math.floor(size / 14);
  const mask = textMask('mv', scale);
  const ox = Math.floor((size - mask.width) / 2);
  const oy = Math.floor((size - mask.height) / 2) - scale * 1;
  return png(size, size, (x, y) => {
    // abgerundetes Quadrat mit Kantenglättung
    const cx = Math.min(Math.max(x + 0.5, radius), size - radius);
    const cy = Math.min(Math.max(y + 0.5, radius), size - radius);
    const dist = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
    const alpha = Math.max(0, Math.min(1, radius - dist + 0.5)) * 255;
    const t = (x + y) / (2 * size);
    let color = mix(VIOLET, CYAN, t);
    if (mask.at(x - ox, y - oy)) color = [255, 255, 255];
    else if (mask.at(x - ox - scale / 3, y - oy - scale / 3)) color = mix(color, DARK, 0.35); // Schatten
    return [...color, alpha];
  });
}

save(join(repo, 'launcher', 'assets', 'icon.png'), icon(256));
save(join(repo, 'mod', 'src', 'main', 'resources', 'assets', 'moonvibe', 'icon.png'), icon(128));

// ---------- Capes (64x32, Minecraft-Cape-Layout) ----------
// Vorderseite: x 1–10, y 1–16 · Rückseite: x 12–21 · Elytra: ab x 22
function cape(design) {
  return png(64, 32, (x, y) => {
    let u;
    let v;
    if (x >= 1 && x <= 10 && y >= 1 && y <= 16) [u, v] = [(x - 1) / 9, (y - 1) / 15];
    else if (x >= 12 && x <= 21 && y >= 1 && y <= 16) [u, v] = [1 - (x - 12) / 9, (y - 1) / 15];
    else [u, v] = [(x % 22) / 21, Math.min(1, y / 21)];
    return design(u, v, x, y);
  });
}

const capes = {
  moonvibe_classic: (u, v, x, y) => {
    let c = mix(VIOLET, CYAN, v);
    // kleines "mv"-Emblem auf der Vorderseite
    const m = textMask('mv', 1);
    if (x >= 1 && x <= 10 && m.at(x - 1, y - 5)) c = [255, 255, 255];
    return c;
  },
  midnight: (u, v, x, y) => {
    const star = (x * 73 + y * 151) % 37 === 0;
    return star ? [255, 255, 230] : mix(hex('#1e1b4b'), hex('#020617'), v);
  },
  flame: (u, v) => {
    const wave = Math.sin(u * Math.PI * 3) * 0.08;
    return mix(hex('#facc15'), hex('#dc2626'), Math.max(0, Math.min(1, v * 1.1 + wave)));
  },
  ocean: (u, v, x, y) => {
    const stripe = Math.sin((y + Math.sin(x * 0.9) * 1.5) * 1.1) > 0.6;
    return stripe ? mix(hex('#a5f3fc'), hex('#38bdf8'), v) : mix(hex('#0ea5e9'), hex('#1e3a8a'), v);
  },
  emerald: (u, v, x, y) => ((x + y) % 4 < 2 ? mix(hex('#34d399'), hex('#065f46'), v) : mix(hex('#10b981'), hex('#064e3b'), v)),
  gold: (u, v, x, y) => {
    const front = x >= 1 && x <= 10 && y >= 1 && y <= 16;
    const back = x >= 12 && x <= 21 && y >= 1 && y <= 16;
    const border = (front && (x === 1 || x === 10 || y === 1 || y === 16)) || (back && (x === 12 || x === 21 || y === 1 || y === 16));
    return border ? hex('#fbbf24') : mix(hex('#1c1917'), hex('#0c0a09'), v);
  },
};

for (const [id, design] of Object.entries(capes)) {
  save(join(repo, 'backend', 'textures', 'capes', `${id}.png`), cape(design));
}
