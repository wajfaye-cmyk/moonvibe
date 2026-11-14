// Brücke zwischen Oberfläche und Hauptprozess: nur diese Funktionen sind für die Webseite erreichbar.
import { contextBridge, ipcRenderer } from 'electron';
import type { MoonApi } from '../shared/types';

const invoke = (channel: string, ...args: unknown[]) => ipcRenderer.invoke(channel, ...args);

const api: MoonApi = {
  appInfo: () => invoke('app-info'),
  getVersions: () => invoke('versions'),
  getSettings: () => invoke('settings:get'),
  saveSettings: (patch) => invoke('settings:save', patch),
  systemMemoryMb: () => invoke('system-memory'),

  accounts: () => invoke('accounts'),
  loginMicrosoft: () => invoke('accounts:microsoft'),
  addOffline: (name) => invoke('accounts:offline', name),
  selectAccount: (id) => invoke('accounts:select', id),
  removeAccount: (id) => invoke('accounts:remove', id),
  skin: () => invoke('skin'),
  setSkin: (upload) => invoke('skin:set', upload),
  removeSkin: () => invoke('skin:remove'),

  play: (version, server) => invoke('play', version, server),
  servers: () => invoke('servers'),
  news: () => invoke('news'),
  panorama: () => invoke('panorama'),
  gameState: () => invoke('game:state'),
  killGame: () => invoke('game:kill'),

  search: (params) => invoke('mods:search', params),
  installed: (version, type) => invoke('mods:installed', version, type),
  install: (version, projectId, type) => invoke('mods:install', version, projectId, type),
  removeInstalled: (version, type, file) => invoke('mods:remove', version, type, file),
  toggleInstalled: (version, type, file) => invoke('mods:toggle', version, type, file),
  shadersSupported: (version) => invoke('mods:shaders-supported', version),

  cosmetics: () => invoke('cosmetics'),
  cosmeticModel: (id) => invoke('cosmetics:model', id),
  equip: (slot, id) => invoke('cosmetics:equip', slot, id),
  pickImage: () => invoke('cosmetics:pick-image'),
  uploadCape: (png) => invoke('cosmetics:upload-cape', png),
  removeCustomCape: () => invoke('cosmetics:remove-cape'),

  openFolder: (kind, version) => invoke('open-folder', kind, version),
  windowControl: (action) => ipcRenderer.send('window', action),

  onProgress: (cb) => void ipcRenderer.on('progress', (_e, p) => cb(p)),
  onGameState: (cb) => void ipcRenderer.on('game-state', (_e, s) => cb(s)),
  onGameLog: (cb) => void ipcRenderer.on('game-log', (_e, line) => cb(line)),
};

contextBridge.exposeInMainWorld('mv', api);
