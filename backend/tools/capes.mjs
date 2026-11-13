// Eigene Capes der Spieler prüfen und unpassende entfernen.
//
//   npm run capes                      lädt alle eigenen Capes nach data/capes/ (Dateiname = Prüfsumme) zum Ansehen
//   npm run capes -- remove <prüfsumme>  entfernt dieses Cape sofort bei allen Spielern
//
// Arbeitet mit dem Online-Server (vorher einmal "npx wrangler login"); mit --local mit dem Testserver auf dem PC.
import { execFileSync } from 'node:child_process';
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const WRANGLER = join(ROOT, 'node_modules', 'wrangler', 'bin', 'wrangler.js');
const args = process.argv.slice(2);
const where = args.includes('--local') ? '--local' : '--remote';
const [command, hash] = args.filter((a) => a !== '--local');

const wrangler = (...params) => execFileSync(process.execPath, [WRANGLER, ...params, where], { cwd: ROOT, maxBuffer: 64 * 1024 * 1024 });
const sql = (query) => JSON.parse(wrangler('d1', 'execute', 'moonvibe', '--json', '--command', query).toString('utf8'))[0].results;

if (command === 'remove') {
  if (!/^[0-9a-f]{16}$/.test(hash ?? '')) {
    console.error('Usage: npm run capes -- remove <16-digit checksum from the file name>');
    process.exit(1);
  }
  sql(`UPDATE players SET cape = CASE WHEN cape = 'custom_${hash}' THEN NULL ELSE cape END, custom_cape = NULL WHERE custom_cape = '${hash}'`);
  wrangler('kv', 'key', 'delete', hash, '--binding', 'CAPES');
  rmSync(join(ROOT, 'data', 'capes', `${hash}.png`), { force: true });
  console.log(`Cape ${hash} removed.`);
} else {
  const dir = join(ROOT, 'data', 'capes');
  rmSync(dir, { recursive: true, force: true });
  mkdirSync(dir, { recursive: true });
  const capes = sql('SELECT custom_cape AS hash, COUNT(*) AS players FROM players WHERE custom_cape IS NOT NULL GROUP BY custom_cape');
  for (const { hash: id, players } of capes) {
    writeFileSync(join(dir, `${id}.png`), wrangler('kv', 'key', 'get', id, '--binding', 'CAPES'));
    console.log(`${id}.png  (${players} player${players === 1 ? '' : 's'})`);
  }
  console.log(`${capes.length} custom capes saved to ${dir}`);
}
