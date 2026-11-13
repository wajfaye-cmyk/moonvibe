// Bereitet den Cosmetics-Server für Cloudflare vor (läuft automatisch vor "wrangler dev" und "wrangler deploy"):
// Modelle, Texturen und Vorschaubilder nach public/ (liefert Cloudflare direkt aus) und den Katalog mit Pfaden
// nach .build/catalog.json (liest der Worker).
import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const PUBLIC = join(ROOT, 'public');
const FOLDERS = { cape: 'capes', hat: 'hats', face: 'face', back: 'back', wings: 'wings', shoes: 'shoes', pet: 'pets' };

rmSync(PUBLIC, { recursive: true, force: true });
for (const dir of ['models', 'textures', 'thumbnails']) cpSync(join(ROOT, dir), join(PUBLIC, dir), { recursive: true });

writeFileSync(
  join(PUBLIC, '_headers'),
  `/*
  Access-Control-Allow-Origin: *
  X-Content-Type-Options: nosniff
/textures/*
  Cache-Control: public, max-age=3600
/thumbnails/*
  Cache-Control: public, max-age=3600
/models/*
  Cache-Control: public, max-age=600
`,
);

const catalog = JSON.parse(readFileSync(join(ROOT, 'cosmetics.json'), 'utf8')).map((c) => ({
  id: c.id,
  type: c.type,
  rarity: c.rarity ?? 'common',
  name: c.names?.en ?? c.id,
  names: c.names ?? {},
  texture: `/textures/${FOLDERS[c.type]}/${c.id}.png`,
  model: c.type === 'cape' ? null : `/models/${c.id}.json`,
  thumbnail: existsSync(join(ROOT, 'thumbnails', `${c.id}.png`)) ? `/thumbnails/${c.id}.png` : null,
}));
mkdirSync(join(ROOT, '.build'), { recursive: true });
writeFileSync(join(ROOT, '.build', 'catalog.json'), JSON.stringify(catalog));
console.log(`moonvibe: ${catalog.length} cosmetics prepared`);
