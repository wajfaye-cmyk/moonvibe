// Speichert Einstellungen und Accounts als JSON im moonvibe-Ordner.
import { app, safeStorage } from 'electron';
import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import { join } from 'node:path';
import { isLang } from '../shared/i18n';
import type { Settings } from '../shared/types';
import { API_URL, LOCAL_API_URL } from './constants';

export const DATA_DIR = join(app.getPath('appData'), '.moonvibe');
mkdirSync(DATA_DIR, { recursive: true });

/** Ordner mit den mitgelieferten moonvibe-Mods (ein Unterordner pro Minecraft-Version). */
export const CLIENT_MODS_DIR = app.isPackaged ? join(process.resourcesPath, 'client-mods') : join(app.getAppPath(), 'client-mods');

/** Offline-Accounts nur beim Entwickeln – im fertigen Launcher braucht man einen echten Minecraft-Account. */
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

/** Standard-Cosmetics-Server: beim Entwickeln der Testserver auf dem PC, sonst API_URL (leer = keiner). */
export const DEFAULT_API_URL = DEV_MODE ? LOCAL_API_URL : API_URL;

let settings: Settings = readJson('launcher.json', DEFAULT_SETTINGS);
if (!isLang(settings.language)) settings.language = 'en';
// Früher wurde die Standard-Adresse mitgespeichert – die gilt nicht mehr als eigene Eingabe.
if (settings.apiUrl === 'http://localhost:8787' || settings.apiUrl === DEFAULT_API_URL) settings.apiUrl = '';

export function getSettings(): Settings {
  return settings;
}

/** Adresse des Cosmetics-Servers: selbst eingetragen oder Standard ('' = keiner eingerichtet). */
export function apiUrl(): string {
  return (settings.apiUrl.trim() || DEFAULT_API_URL).replace(/\/+$/, '');
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
}

interface AccountsFile {
  accounts: StoredAccount[];
  selectedId: string | null;
  /** Zufällige Kennung dieser Installation (Minecraft möchte eine "clientId"). */
  clientId: string;
}

let accountsFile: AccountsFile = readJson<AccountsFile>('accounts.json', { accounts: [], selectedId: null, clientId: '' });
if (!accountsFile.clientId) {
  accountsFile.clientId = Buffer.from(randomUUID()).toString('base64');
  writeJson('accounts.json', accountsFile);
}

export function getAccountsFile(): AccountsFile {
  return accountsFile;
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
