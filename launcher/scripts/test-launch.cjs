// Entwickler-Test ohne Oberfläche: installiert eine Version und startet sie mit einem Offline-Account.
// Aufruf (nach "npm run build"):  node scripts/test-launch.cjs 26.3 [sekunden]
const { existsSync } = require('node:fs');
const { join } = require('node:path');
const os = require('node:os');
const { installVersion } = require('../dist/core/install');
const { listGameVersions } = require('../dist/core/versions');
const { moonvibeJava, moonvibeVersions, instanceDir, openInstance } = require('../dist/core/instances');
const { launchGame } = require('../dist/core/launch');
const { offlineUuid } = require('../dist/core/microsoft');

const version = process.argv[2] ?? '26.3';
const seconds = Number(process.argv[3] ?? 60);
const dataDir = join(process.env.APPDATA ?? join(os.homedir(), '.config'), '.moonvibe');
const clientMods = join(__dirname, '..', 'client-mods');

(async () => {
  const versions = await listGameVersions(dataDir, await moonvibeVersions(clientMods));
  const info = versions.find((v) => v.id === version);
  if (!info) throw new Error(`Version ${version} nicht gefunden`);
  console.log(`[test] ${version} loader=${info.loader} moonvibe=${info.moonvibe}`);

  let lastStage = '';
  const minJava = info.moonvibe ? await moonvibeJava(clientMods, version) : 0;
  const install = await installVersion(dataDir, version, info.loader, (p) => {
    if (p.stage !== lastStage || p.done === p.total) {
      console.log(`[test] ${p.stage}: ${p.done}/${p.total}`);
      lastStage = p.stage;
    }
  }, minJava);
  await openInstance(dataDir, version, info.loader).syncPack(clientMods, (m) => console.log('[pack]', m));
  console.log('[test] mods:', (await openInstance(dataDir, version, info.loader).list('mod')).map((m) => `${m.title} (${m.source})`).join(', '));

  const worldExists = !!process.env.QUICKPLAY && existsSync(join(instanceDir(dataDir, version), 'saves', process.env.QUICKPLAY));
  const child = await launchGame({
    install,
    gameDir: instanceDir(dataDir, version),
    account: { name: 'MoonTester', uuid: offlineUuid('MoonTester'), accessToken: '0', xuid: '0', userType: 'legacy' },
    clientId: 'dGVzdA==',
    memoryMb: 3072,
    systemProperties: {
      'moonvibe.api': 'http://127.0.0.1:8777',
      // Entwickler-Bildschirmfotos der Mod (siehe DevScreenshots.java)
      ...(process.env.MV_SHOTS ? { 'moonvibe.dev.screenshots': process.env.MV_SHOTS } : {}),
      ...(process.env.MV_SHOTS && process.env.QUICKPLAY ? { 'moonvibe.dev.world': 'true' } : {}),
      // Welt gibt es in dieser Version noch nicht → die Mod legt selbst eine an
      ...(process.env.MV_SHOTS && process.env.QUICKPLAY && !worldExists ? { 'moonvibe.dev.newworld': 'true' } : {}),
      // Bild, das im Test als eigenes Cape hochgeladen wird
      ...(process.env.MV_CAPE ? { 'moonvibe.dev.cape': process.env.MV_CAPE } : {}),
    },
    extraGameArgs: process.env.QUICKPLAY && worldExists ? ['--quickPlaySingleplayer', process.env.QUICKPLAY] : [],
  });
  console.log(`[test] pid ${child.pid}`);
  const onLine = (prefix) => (buf) => {
    for (const line of buf.toString().split(/\r?\n/)) if (line.trim()) console.log(prefix, line);
  };
  child.stdout.on('data', onLine('[mc]'));
  child.stderr.on('data', onLine('[mc!]'));
  child.on('exit', (code) => {
    console.log(`[test] Minecraft beendet mit Code ${code}`);
    process.exit(0);
  });
  setTimeout(() => {
    console.log('[test] Zeit abgelaufen, beende Minecraft');
    child.kill();
  }, seconds * 1000);
})().catch((e) => {
  console.error('[test] FEHLER', e);
  process.exit(1);
});
