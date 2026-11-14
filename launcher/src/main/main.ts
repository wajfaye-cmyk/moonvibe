// Einstiegspunkt des Launchers: Fenster erstellen und die Oberfläche mit dem Kern verbinden.
import { app, BrowserWindow, dialog, ipcMain, shell } from 'electron';
import { readFile, stat } from 'node:fs/promises';
import os from 'node:os';
import { join } from 'node:path';
import { LocalizedError, translate } from '../shared/i18n';
import type { CosmeticSlot, ProjectType, SearchParams, Settings, SkinUpload } from '../shared/types';
import { startLocalCosmetics } from '../core/localserver';
import { LAUNCHER_VERSION } from '../core/http';
import { instanceDir, openInstance } from '../core/instances';
import { search, shadersSupported } from '../core/modrinth';
import { accountsState, addOffline, loginMicrosoft, removeAccount, selectAccount } from './accounts';
import { cosmeticModel, cosmeticsState, equip, news, removeCustomCape, uploadCape } from './cosmetics';
import { runDevTest } from './devtest';
import { gameState, getVersions, killGame, play, versionInfo } from './game';
import { panoramaImages } from './panorama';
import { serverList } from './servers';
import { currentSkin, removeSkin, setSkin } from './skin';
import { COSMETICS_ASSETS_DIR, DATA_DIR, DEFAULT_API_URL, DEV_MODE, getSettings, LOCAL_COSMETICS_DIR, saveSettings, setLocalApiUrl } from './store';

let win: BrowserWindow | null = null;

function createWindow(): void {
  win = new BrowserWindow({
    width: 1180,
    height: 720,
    minWidth: 980,
    minHeight: 620,
    frame: false,
    show: false,
    backgroundColor: '#05080f',
    title: 'moonvibe',
    icon: join(__dirname, '..', 'renderer', 'assets', 'icon.png'),
    webPreferences: {
      preload: join(__dirname, '..', 'preload', 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  win.once('ready-to-show', () => win?.show());
  // Links (z. B. zu Modrinth) im normalen Browser öffnen, nicht im Launcher
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://')) void shell.openExternal(url);
    return { action: 'deny' };
  });
  if (DEV_MODE && process.env.MOONVIBE_SCREENSHOTS) void runDevTest(win, process.env.MOONVIBE_SCREENSHOTS);
  void win.loadFile(join(__dirname, '..', 'renderer', 'index.html'));
}

function mainWindow(): BrowserWindow {
  if (!win) throw new Error('Kein Fenster');
  return win;
}

/** ipcMain.handle mit verständlichen Fehlermeldungen (in der eingestellten Sprache) für die Oberfläche. */
function handle<A extends unknown[]>(channel: string, fn: (...args: A) => unknown): void {
  ipcMain.handle(channel, async (_event, ...args) => {
    try {
      return await fn(...(args as A));
    } catch (e) {
      if (e instanceof LocalizedError) throw new Error(translate(getSettings().language, e.key, e.params));
      throw new Error((e as Error).message);
    }
  });
}

const IMAGE_TYPES: Record<string, string> = {
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  webp: 'image/webp',
  gif: 'image/gif',
  bmp: 'image/bmp',
};

/** Bild für ein eigenes Cape auswählen; die Oberfläche bekommt es als data:-URL und baut daraus das Cape. */
async function pickImage(): Promise<string | null> {
  // automatischer Test: festes Bild statt Dateidialog
  const testFile = DEV_MODE ? process.env.MOONVIBE_TEST_CAPE : undefined;
  const result = testFile
    ? { canceled: false, filePaths: [testFile] }
    : await dialog.showOpenDialog(mainWindow(), {
        properties: ['openFile'],
        filters: [{ name: translate(getSettings().language, 'cosmetics.images'), extensions: Object.keys(IMAGE_TYPES) }],
      });
  const file = result.filePaths[0];
  if (result.canceled || !file) return null;
  const type = IMAGE_TYPES[file.split('.').pop()?.toLowerCase() ?? ''];
  if (!type) throw new LocalizedError('cosmetics.imageError');
  if ((await stat(file)).size > 15 * 1024 * 1024) throw new LocalizedError('cosmetics.imageTooBig');
  return `data:${type};base64,${(await readFile(file)).toString('base64')}`;
}

function registerIpc(): void {
  handle('app-info', () => ({ version: LAUNCHER_VERSION, dataDir: DATA_DIR, dev: DEV_MODE, defaultApiUrl: DEFAULT_API_URL }));
  handle('versions', () => getVersions());
  handle('settings:get', () => getSettings());
  handle('settings:save', (patch: Partial<Settings>) => saveSettings(patch));
  handle('system-memory', () => Math.floor(os.totalmem() / 1024 / 1024));

  handle('accounts', () => accountsState());
  handle('accounts:microsoft', () => loginMicrosoft(mainWindow()));
  handle('accounts:offline', (name: string) => addOffline(name));
  handle('accounts:select', (id: string) => selectAccount(id));
  handle('accounts:remove', (id: string) => removeAccount(id));
  handle('skin', () => currentSkin());
  handle('skin:set', (upload: SkinUpload) => setSkin(upload));
  handle('skin:remove', () => removeSkin());

  handle('play', (version: string, server?: string) => {
    saveSettings({ lastVersion: version });
    return play(version, mainWindow(), server ?? null);
  });
  handle('servers', () => serverList());
  handle('news', () => news());
  handle('panorama', () => panoramaImages());
  handle('game:state', () => gameState());
  handle('game:kill', () => killGame());

  handle('mods:search', async (params: SearchParams) => search(params, (await versionInfo(params.gameVersion)).loader));
  handle('mods:installed', async (version: string, type: ProjectType) =>
    openInstance(DATA_DIR, version, (await versionInfo(version)).loader).list(type),
  );
  handle('mods:install', async (version: string, projectId: string, type: ProjectType) =>
    openInstance(DATA_DIR, version, (await versionInfo(version)).loader).install(type, projectId),
  );
  handle('mods:remove', async (version: string, type: ProjectType, file: string) =>
    openInstance(DATA_DIR, version, (await versionInfo(version)).loader).remove(type, file),
  );
  handle('mods:toggle', async (version: string, type: ProjectType, file: string) =>
    openInstance(DATA_DIR, version, (await versionInfo(version)).loader).toggle(type, file),
  );
  handle('mods:shaders-supported', async (version: string) => shadersSupported(version, (await versionInfo(version)).loader));

  handle('cosmetics', () => cosmeticsState());
  handle('cosmetics:model', (id: string) => cosmeticModel(id));
  handle('cosmetics:equip', (slot: CosmeticSlot, id: string | null) => equip(slot, id));
  handle('cosmetics:pick-image', () => pickImage());
  handle('cosmetics:upload-cape', (png: Uint8Array) => uploadCape(Buffer.from(png)));
  handle('cosmetics:remove-cape', () => removeCustomCape());

  handle('open-folder', async (kind: 'data' | 'instance' | 'logs', version?: string) => {
    const dir =
      kind === 'data' ? DATA_DIR : kind === 'logs' ? join(instanceDir(DATA_DIR, version ?? ''), 'logs') : instanceDir(DATA_DIR, version ?? '');
    const error = await shell.openPath(dir);
    if (error) throw new LocalizedError('error.folderMissing');
  });

  ipcMain.on('window', (_event, action: 'minimize' | 'close') => {
    if (action === 'minimize') win?.minimize();
    else win?.close();
  });
}

app.setAppUserModelId('net.moonvibe.launcher');
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (win) {
      win.show();
      win.focus();
    }
  });
  void app.whenReady().then(async () => {
    // Built-in cosmetics server (used when no shared server is set up) – the launcher also works without it.
    try {
      setLocalApiUrl((await startLocalCosmetics(COSMETICS_ASSETS_DIR, LOCAL_COSMETICS_DIR)).url);
    } catch (e) {
      console.error('[moonvibe] Built-in cosmetics server could not start:', e);
    }
    registerIpc();
    createWindow();
  });
  app.on('window-all-closed', () => app.quit());
}
