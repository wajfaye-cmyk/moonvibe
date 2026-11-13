// Rendert Vorschaubilder für alle Cosmetics nach backend/thumbnails/ (gleiche 3D-Darstellung wie im Launcher).
// Aufruf: npm run thumbnails   (nach "node tools/generate-cosmetics.mjs" im Backend)
const { app, BrowserWindow } = require('electron');
const { mkdirSync, readFileSync, writeFileSync, existsSync } = require('node:fs');
const { join } = require('node:path');

const backend = join(__dirname, '..', '..', 'backend');
const FOLDERS = { cape: 'capes', hat: 'hats', face: 'face', back: 'back', wings: 'wings', shoes: 'shoes', pet: 'pets' };

app.whenReady().then(async () => {
  const catalog = JSON.parse(readFileSync(join(backend, 'cosmetics.json'), 'utf8'));
  const jobs = catalog.map((c) => {
    const texture = readFileSync(join(backend, 'textures', FOLDERS[c.type], `${c.id}.png`)).toString('base64');
    const modelFile = join(backend, 'models', `${c.id}.json`);
    return {
      id: c.id,
      type: c.type,
      texture: `data:image/png;base64,${texture}`,
      model: existsSync(modelFile) ? JSON.parse(readFileSync(modelFile, 'utf8')) : null,
    };
  });

  const win = new BrowserWindow({ show: false, webPreferences: { contextIsolation: false } });
  await win.loadFile(join(__dirname, '..', 'dist', 'renderer', 'thumbs.html'));
  const images = await win.webContents.executeJavaScript(`renderAll(${JSON.stringify(jobs)})`);

  const out = join(backend, 'thumbnails');
  mkdirSync(out, { recursive: true });
  for (const [id, url] of Object.entries(images)) {
    writeFileSync(join(out, `${id}.png`), Buffer.from(url.split(',')[1], 'base64'));
  }
  console.log(`${Object.keys(images).length} Vorschaubilder gespeichert in ${out}`);
  app.quit();
});
