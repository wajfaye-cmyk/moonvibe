// "Spielen"-Knopf: installieren, moonvibe-Mods aktualisieren, Minecraft starten und überwachen.
import type { BrowserWindow } from 'electron';
import type { ChildProcess } from 'node:child_process';
import { createInterface } from 'node:readline';
import { LocalizedError, minecraftLang } from '../shared/i18n';
import type { GameState, GameVersion, Progress } from '../shared/types';
import { installVersion } from '../core/install';
import { moonvibeJava, moonvibeVersions, instanceDir, openInstance, writeDefaultOptions } from '../core/instances';
import { launchGame } from '../core/launch';
import { listGameVersions } from '../core/versions';
import { launchAccount, selectedAccount } from './accounts';
import { cosmeticsSession } from './cosmetics';
import { apiUrl, CLIENT_MODS_DIR, DATA_DIR, getAccountsFile, getSettings } from './store';

let versionsCache: GameVersion[] | null = null;
let child: ChildProcess | null = null;
let starting = false;
let killedByUser = false;
let state: GameState = { running: false };
const lastLines: string[] = [];

export async function getVersions(): Promise<GameVersion[]> {
  versionsCache ??= await listGameVersions(DATA_DIR, await moonvibeVersions(CLIENT_MODS_DIR));
  return versionsCache;
}

export async function versionInfo(id: string): Promise<GameVersion> {
  const v = (await getVersions()).find((x) => x.id === id);
  if (!v) throw new LocalizedError('error.unknownVersion', { version: id });
  return v;
}

export function gameState(): GameState {
  return state;
}

export function killGame(): void {
  if (!child) return;
  killedByUser = true;
  child.kill();
}

export async function play(version: string, win: BrowserWindow, server: string | null = null): Promise<void> {
  if (child || starting) throw new LocalizedError('error.alreadyRunning');
  starting = true;
  const send = (channel: string, payload: unknown) => {
    if (!win.isDestroyed()) win.webContents.send(channel, payload);
  };
  const progress = (p: Progress | null) => send('progress', p);
  const log = (line: string) => {
    lastLines.push(line);
    if (lastLines.length > 300) lastLines.shift();
    send('game-log', line);
  };

  try {
    const info = await versionInfo(version);
    const account = await launchAccount();
    const minJava = info.moonvibe ? await moonvibeJava(CLIENT_MODS_DIR, version) : 0;
    const install = await installVersion(DATA_DIR, version, info.loader, progress, minJava);

    progress({ stage: 'stage.mods', done: 0, total: 1 });
    await openInstance(DATA_DIR, version, info.loader).syncPack(CLIENT_MODS_DIR, log);

    progress({ stage: 'stage.starting', done: 1, total: 1 });
    const settings = getSettings();
    await writeDefaultOptions(instanceDir(DATA_DIR, version), minecraftLang(settings.language, info.releaseTime));
    // Damit man auch im Spiel Cosmetics anziehen kann – ohne dass die Mod den Minecraft-Token weitergeben muss.
    // Ist der Cosmetics-Server gerade nicht erreichbar, startet das Spiel trotzdem.
    const selected = selectedAccount();
    const cosmetics = selected?.type === 'microsoft' && apiUrl() ? await cosmeticsSession(selected).catch(() => null) : null;
    lastLines.length = 0;
    child = await launchGame({
      install,
      gameDir: instanceDir(DATA_DIR, version),
      account,
      clientId: getAccountsFile().clientId,
      memoryMb: settings.memoryMb,
      width: settings.gameWidth,
      height: settings.gameHeight,
      systemProperties: { 'moonvibe.api': apiUrl(), ...(cosmetics ? { 'moonvibe.session': cosmetics } : {}) },
      joinServer: server,
    });
  } finally {
    starting = false;
    progress(null);
  }

  const proc = child;
  for (const stream of [proc.stdout, proc.stderr]) {
    if (stream) createInterface({ input: stream }).on('line', log);
  }

  state = { running: true, version };
  send('game-state', state);
  if (getSettings().launcherBehavior === 'hide') win.hide();

  killedByUser = false;
  const finished = (code: number | null, crashed: boolean) => {
    if (child !== proc) return;
    child = null;
    state = { running: false, exitCode: code, crashed, lastLines: crashed ? lastLines.slice(-60) : [] };
    if (!win.isDestroyed()) {
      win.show();
      send('game-state', state);
    }
  };
  // Java ließ sich gar nicht starten: dann kommt oft kein "exit" – Launcher trotzdem wieder zeigen
  proc.on('error', (e) => {
    log(`[moonvibe] Could not start Java: ${e.message}`);
    finished(null, true);
  });
  proc.on('exit', (code) => finished(code, code !== 0 && code !== null && !killedByUser));
}
