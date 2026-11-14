// Account-Verwaltung: Username-Accounts (kein Login nötig), Microsoft-Login im eigenen Fenster, Token-Erneuerung.
import { BrowserWindow, session } from 'electron';
import { randomUUID } from 'node:crypto';
import { LocalizedError, translate } from '../shared/i18n';
import type { AccountsState } from '../shared/types';
import type { LaunchAccount } from '../core/launch';
import {
  authorizeUrl,
  createPkce,
  exchangeCode,
  loginMinecraft,
  offlineUuid,
  REDIRECT_URI,
  refreshMicrosoft,
  type MinecraftSession,
} from '../core/microsoft';
import { MS_CLIENT_ID } from './constants';
import { rmSync } from 'node:fs';
import { join } from 'node:path';
import { getAccountsFile, getSettings, protect, saveAccountsFile, SKINS_DIR, unprotect, type StoredAccount } from './store';

export function accountsState(): AccountsState {
  const file = getAccountsFile();
  return {
    accounts: file.accounts.map(({ id, type, name, uuid, skin, avatar }) => ({
      id,
      type,
      name,
      uuid,
      avatar: type === 'offline' && skin ? (avatar ?? null) : null,
      skin: type === 'offline' ? (skin ?? null) : null,
    })),
    selectedId: file.selectedId,
    offlineAllowed: true,
    microsoftAvailable: Boolean(MS_CLIENT_ID),
  };
}

function storeSession(existingId: string | null, msRefreshToken: string, s: MinecraftSession): void {
  saveAccountsFile((file) => {
    const id = existingId ?? file.accounts.find((a) => a.type === 'microsoft' && a.uuid === s.uuid)?.id ?? randomUUID();
    const account: StoredAccount = {
      id,
      type: 'microsoft',
      name: s.name,
      uuid: s.uuid,
      msRefreshToken: protect(msRefreshToken),
      mcAccessToken: protect(s.accessToken),
      mcExpiresAt: s.expiresAt,
      xuid: s.xuid,
    };
    file.accounts = [...file.accounts.filter((a) => a.id !== id), account];
    file.selectedId = id;
  });
}

/** Öffnet das Microsoft-Anmeldefenster und wartet auf den Code. */
function askForCode(parent: BrowserWindow, url: string, state: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const win = new BrowserWindow({
      parent,
      modal: true,
      width: 520,
      height: 680,
      title: translate(getSettings().language, 'account.microsoft'),
      autoHideMenuBar: true,
      backgroundColor: '#ffffff',
      webPreferences: {
        // eigene, flüchtige Sitzung: keine Cookies bleiben im Launcher hängen
        session: session.fromPartition(`ms-login-${Date.now()}`),
        sandbox: true,
        contextIsolation: true,
        nodeIntegration: false,
      },
    });
    let done = false;
    const check = (target: string) => {
      if (!target.startsWith(REDIRECT_URI)) return;
      done = true;
      const params = new URL(target).searchParams;
      win.close();
      if (params.get('state') !== state) return reject(new LocalizedError('error.invalidResponse'));
      const code = params.get('code');
      if (code) resolve(code);
      else reject(params.get('error_description') ? new Error(params.get('error_description')!) : new LocalizedError('error.loginCancelled'));
    };
    win.webContents.on('will-redirect', (_e, target) => check(target));
    win.webContents.on('will-navigate', (_e, target) => check(target));
    win.webContents.on('did-navigate', (_e, target) => check(target));
    win.on('closed', () => {
      if (!done) reject(new LocalizedError('error.loginCancelled'));
    });
    void win.loadURL(url);
  });
}

export async function loginMicrosoft(parent: BrowserWindow): Promise<AccountsState> {
  if (!MS_CLIENT_ID) {
    throw new LocalizedError('error.noClientId');
  }
  const pkce = createPkce();
  const code = await askForCode(parent, authorizeUrl(MS_CLIENT_ID, pkce.challenge, pkce.state), pkce.state);
  const tokens = await exchangeCode(MS_CLIENT_ID, code, pkce.verifier);
  const mcSession = await loginMinecraft(tokens.accessToken);
  storeSession(null, tokens.refreshToken, mcSession);
  return accountsState();
}

/** Username account: just a name, no login (like TLauncher). The same name always gets the same UUID. */
export function addOffline(name: string): AccountsState {
  if (!/^[A-Za-z0-9_]{3,16}$/.test(name)) throw new LocalizedError('error.invalidName');
  saveAccountsFile((file) => {
    const existing = file.accounts.find((a) => a.type === 'offline' && a.name.toLowerCase() === name.toLowerCase());
    if (existing) {
      file.selectedId = existing.id;
      return;
    }
    const id = randomUUID();
    file.accounts.push({ id, type: 'offline', name, uuid: offlineUuid(name) });
    file.selectedId = id;
  });
  return accountsState();
}

export function selectAccount(id: string): AccountsState {
  saveAccountsFile((file) => {
    if (file.accounts.some((a) => a.id === id)) file.selectedId = id;
  });
  return accountsState();
}

export function removeAccount(id: string): AccountsState {
  const removed = getAccountsFile().accounts.find((a) => a.id === id);
  if (removed?.type === 'offline' && /^[0-9a-f]{32}$/.test(removed.uuid)) {
    for (const suffix of ['', '.wide', '.slim']) rmSync(join(SKINS_DIR, `${removed.uuid}${suffix}.png`), { force: true });
  }
  saveAccountsFile((file) => {
    file.accounts = file.accounts.filter((a) => a.id !== id);
    if (file.selectedId === id) file.selectedId = file.accounts[0]?.id ?? null;
  });
  return accountsState();
}

export function selectedAccount(): StoredAccount | null {
  const file = getAccountsFile();
  return file.accounts.find((a) => a.id === file.selectedId) ?? null;
}

/** Gültiger Minecraft-Token des gewählten Accounts (wird bei Bedarf erneuert). */
export async function validMinecraftToken(account: StoredAccount): Promise<string> {
  const token = unprotect(account.mcAccessToken);
  if (token && account.mcExpiresAt && account.mcExpiresAt - Date.now() > 5 * 60 * 1000) return token;

  const refresh = unprotect(account.msRefreshToken);
  if (!refresh || !MS_CLIENT_ID) throw new LocalizedError('error.reloginRequired');
  const tokens = await refreshMicrosoft(MS_CLIENT_ID, refresh);
  const mcSession = await loginMinecraft(tokens.accessToken);
  storeSession(account.id, tokens.refreshToken, mcSession);
  return mcSession.accessToken;
}

export async function launchAccount(): Promise<LaunchAccount> {
  const account = selectedAccount();
  if (!account) throw new LocalizedError('error.notLoggedIn');
  if (account.type === 'offline') {
    return { name: account.name, uuid: account.uuid, accessToken: '0', xuid: '0', userType: 'legacy' };
  }
  const accessToken = await validMinecraftToken(account);
  const fresh = selectedAccount()!;
  return { name: fresh.name, uuid: fresh.uuid, accessToken, xuid: fresh.xuid ?? '0', userType: 'msa' };
}
