// Baut die Oberfläche: TypeScript + three.js → eine Datei (esbuild), dazu HTML/CSS/Bilder kopieren.
import { build } from 'esbuild';
import { cpSync, existsSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const out = join(root, 'dist', 'renderer');
mkdirSync(out, { recursive: true });

await build({
  entryPoints: { app: join(root, 'src', 'renderer', 'app.ts'), thumbs: join(root, 'src', 'renderer', 'thumbs.ts') },
  outdir: out,
  bundle: true,
  format: 'esm',
  target: 'chrome130',
  sourcemap: true,
  minify: process.argv.includes('--minify'),
  logLevel: 'warning',
});

for (const file of ['index.html', 'styles.css', 'thumbs.html']) {
  cpSync(join(root, 'src', 'renderer', file), join(out, file));
}
cpSync(join(root, 'assets'), join(out, 'assets'), { recursive: true });
// News mitliefern – falls der Cosmetics-Server mal nicht erreichbar ist, bleibt die Startseite nicht leer
const news = join(root, '..', 'backend', 'news.json');
if (existsSync(news)) cpSync(news, join(out, 'assets', 'news', 'news.json'));
