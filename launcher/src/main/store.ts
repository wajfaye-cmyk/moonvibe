// Speichert Einstellungen und Accounts als JSON im moonvibe-Ordner.
import { app, safeStorage } from 'electron';
import { randomBytes, randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import { join } from 'node:path';
import { isLang } from '../shared/i18n';
import type { Settings } from '../shared/types';
import { API_URL } from './constants';

export const DATA_DIR = join(app.getPath('appData'), '.moonvibe');
mkdirSync(DATA_DIR, { recursive: true });

/** Ordner mit den mitgelieferten moonvibe-Mods (ein Unterordner pro Minecraft-Version). */
export const CLIENT_MODS_DIR = app.isPackaged ? join(process.resourcesPath, 'client-mods') : join(app.getAppPath(), 'client-mods');

/** Cosmetics catalog, models, textures and thumbnails for the built-in cosmetics server. */
export const COSMETICS_ASSETS_DIR = app.isPackaged ? join(process.resourcesPath, 'cosmetics') : join(app.getAppPath(), '..', 'backend');

/** Data of the built-in cosmetics server (who wears what, uploaded capes). */
export const LOCAL_COSMETICS_DIR = join(DATA_DIR, 'cosmetics');

/** Uploaded skins of username accounts (<uuid>.png = original, .wide.png / .slim.png = per arm model). */
export const SKINS_DIR = join(DATA_DIR, 'skins');

/** Developer mode (started from source): enables the automatic UI test. */
export const DEV_MODE = !app.isPackaged;

function readJson<T>(file: string, fallback: T): T {
  try {
    return { ...fallback, ...JSON.parse(readFileSync(join(DATA_DIR, file), 'utf8')) };
  } catch {
    return fallback;
  }
}

function writeJson(file: string, data: unknown): void {
  writeFileSync(join(DATA_DIR, file), JSON.stringify(data, null, 2));
}

const totalMb = Math.floor(os.totalmem() / 1024 / 1024);
const DEFAULT_SETTINGS: Settings = {
  language: 'en',
  customServers: [],
  // Standard: ein Viertel des Arbeitsspeichers, mindestens 2 GB, höchstens 6 GB
  memoryMb: Math.min(6144, Math.max(2048, Math.round(totalMb / 4 / 512) * 512)),
  lastVersion: null,
  launcherBehavior: 'hide',
  // leer = Standard-Server (siehe apiUrl())
  apiUrl: '',
  gameWidth: null,
  gameHeight: null,
};

/** Shared cosmetics server built into this launcher ('' = none, then the built-in server on this PC is used). */
export const DEFAULT_API_URL = API_URL;

/** Address of the built-in cosmetics server ('' until it has started, or if it could not start). */
let localApiUrl = '';

export function setLocalApiUrl(url: string): void {
  localApiUrl = url;
}

let settings: Settings = readJson('launcher.json', DEFAULT_SETTINGS);
if (!isLang(settings.language)) settings.language = 'en';
// Früher wurde die Standard-Adresse mitgespeichert – die gilt nicht mehr als eigene Eingabe.
if (settings.apiUrl === 'http://localhost:8787' || settings.apiUrl === DEFAULT_API_URL) settings.apiUrl = '';

export function getSettings(): Settings {
  return settings;
}

/** Address of a shared cosmetics server: entered in the settings or built in ('' = none). */
function remoteApiUrl(): string {
  return (settings.apiUrl.trim() || DEFAULT_API_URL).replace(/\/+$/, '');
}

/** Cosmetics server in use: the shared one if set up, otherwise the built-in one on this PC. */
export function apiUrl(): string {
  return remoteApiUrl() || localApiUrl;
}

/** true = cosmetics are stored on this PC only (built-in server). */
export function isLocalApi(): boolean {
  return !remoteApiUrl() && Boolean(localApiUrl);
}

export function saveSettings(patch: Partial<Settings>): Settings {
  settings = { ...settings, ...patch };
  if (!isLang(settings.language)) settings.language = 'en';
  writeJson('launcher.json', settings);
  return settings;
}

// ---- Accounts ----

export interface StoredAccount {
  id: string;
  type: 'microsoft' | 'offline';
  name: string;
  uuid: string;
  msRefreshToken?: string;
  mcAccessToken?: string;
  mcExpiresAt?: number;
  xuid?: string;
  /** Username accounts: an own skin was uploaded (files in SKINS_DIR); slim = 3 px arms */
  skin?: { slim: boolean };
  /** Username accounts: face of the uploaded skin as data: URL (for the account picture) */
  avatar?: string;
}

interface AccountsFile {
  accounts: StoredAccount[];
  selectedId: string | null;
  /** Zufällige Kennung dieser Installation (Minecraft möchte eine "clientId"). */
  clientId: string;
  /** Secret of this installation: reserves the usernames of its username accounts on a shared cosmetics server. */
  offlineKey?: string;
}

let accountsFile: AccountsFile = readJson<AccountsFile>('accounts.json', { accounts: [], selectedId: null, clientId: '' });
if (!accountsFile.clientId) {
  accountsFile.clientId = Buffer.from(randomUUID()).toString('base64');
  writeJson('accounts.json', accountsFile);
}

export function getAccountsFile(): AccountsFile {
  return accountsFile;
}

export function offlineKey(): string {
  if (!accountsFile.offlineKey) {
    accountsFile.offlineKey = randomBytes(32).toString('hex');
    writeJson('accounts.json', accountsFile);
  }
  return accountsFile.offlineKey;
}

export function saveAccountsFile(update: (file: AccountsFile) => void): AccountsFile {
  update(accountsFile);
  writeJson('accounts.json', accountsFile);
  return accountsFile;
}

/** Tokens werden mit dem Windows-Schlüsselspeicher verschlüsselt abgelegt. */
export function protect(value: string): string {
  if (safeStorage.isEncryptionAvailable()) return `enc:${safeStorage.encryptString(value).toString('base64')}`;
  return `plain:${Buffer.from(value).toString('base64')}`;
}

export function unprotect(value: string | undefined): string | null {
  if (!value) return null;
  try {
    if (value.startsWith('enc:')) return safeStorage.decryptString(Buffer.from(value.slice(4), 'base64'));
    if (value.startsWith('plain:')) return Buffer.from(value.slice(6), 'base64').toString('utf8');
  } catch {
    // z. B. nach Neuinstallation von Windows nicht mehr entschlüsselbar
  }
  return null;
}
