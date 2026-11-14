// Skin des angemeldeten Spielers für die 3D-Vorschau im Launcher – und eigene Skins für Username-Accounts.
import AdmZip from 'adm-zip';
import { mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { LocalizedError } from '../shared/i18n';
import type { AccountsState, SkinInfo, SkinUpload } from '../shared/types';
import type { SkinFiles } from '../core/skinpack';
import { fetchJson, USER_AGENT } from '../core/http';
import { accountsState, selectedAccount } from './accounts';
import { DATA_DIR, saveAccountsFile, SKINS_DIR, type StoredAccount } from './store';

const cache = new Map<string, SkinInfo>();

async function mojangSkin(uuid: string): Promise<SkinInfo | null> {
  const profile = await fetchJson<{ properties: { name: string; value: string }[] }>(
    `https://sessionserver.mojang.com/session/minecraft/profile/${uuid}`,
  );
  const textures = profile.properties.find((p) => p.name === 'textures');
  if (!textures) return null;
  const data = JSON.parse(Buffer.from(textures.value, 'base64').toString('utf8')) as {
    textures: { SKIN?: { url: string; metadata?: { model?: string } } };
  };
  const skin = data.textures.SKIN;
  if (!skin) return null;
  const res = await fetch(skin.url.replace(/^http:/, 'https:'), { headers: { 'User-Agent': USER_AGENT } });
  if (!res.ok) return null;
  const png = Buffer.from(await res.arrayBuffer());
  return { dataUrl: `data:image/png;base64,${png.toString('base64')}`, slim: skin.metadata?.model === 'slim' };
}

/** Standard-Skin (Steve) aus einer bereits installierten Minecraft-Version holen. */
function defaultSkin(): SkinInfo {
  try {
    for (const version of readdirSync(join(DATA_DIR, 'versions'))) {
      const jar = join(DATA_DIR, 'versions', version, `${version}.jar`);
      try {
        const entry = new AdmZip(jar).getEntry('assets/minecraft/textures/entity/player/wide/steve.png');
        if (entry) return { dataUrl: `data:image/png;base64,${entry.getData().toString('base64')}`, slim: false };
      } catch {
        // diese Version hat keine Jar – nächste probieren
      }
    }
  } catch {
    // noch nichts installiert
  }
  return { dataUrl: null, slim: false };
}

export async function currentSkin(): Promise<SkinInfo> {
  const account = selectedAccount();
  const key = account ? `${account.type}:${account.uuid}` : 'none';
  const cached = cache.get(key);
  if (cached) return cached;

  let info: SkinInfo | null = null;
  if (account?.type === 'microsoft') info = await mojangSkin(account.uuid).catch(() => null);
  if (account?.type === 'offline') info = uploadedSkin(account);
  info ??= defaultSkin();
  if (info.dataUrl) cache.set(key, info);
  return info;
}

// ---------- Own skins of username accounts ----------

const skinFile = (uuid: string, variant: '' | '.wide' | '.slim') => join(SKINS_DIR, `${uuid}${variant}.png`);

function uploadedSkin(account: StoredAccount): SkinInfo | null {
  if (!account.skin) return null;
  try {
    return { dataUrl: `data:image/png;base64,${readFileSync(skinFile(account.uuid, '')).toString('base64')}`, slim: account.skin.slim };
  } catch {
    return null;
  }
}

/** 64×64 PNG? (reads the size from the PNG header) */
function isSkinPng(png: Buffer): boolean {
  const signature = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
  if (png.length < 24 || png.length > 200_000 || signature.some((b, i) => png[i] !== b)) return false;
  return png.readUInt32BE(16) === 64 && png.readUInt32BE(20) === 64;
}

function offlineSelected(): StoredAccount {
  const account = selectedAccount();
  if (!account) throw new LocalizedError('error.notLoggedIn');
  if (account.type !== 'offline' || !/^[0-9a-f]{32}$/.test(account.uuid)) throw new LocalizedError('skin.offlineOnly');
  return account;
}

/** Saves the uploaded skin of the selected username account (the launcher window prepared all three images). */
export function setSkin(upload: SkinUpload): AccountsState {
  const account = offlineSelected();
  const files = { '': Buffer.from(upload.png), '.wide': Buffer.from(upload.wide), '.slim': Buffer.from(upload.slim) } as const;
  if (!Object.values(files).every(isSkinPng)) throw new LocalizedError('skin.invalid');
  const avatar = typeof upload.avatar === 'string' && upload.avatar.startsWith('data:image/png;base64,') && upload.avatar.length < 30_000 ? upload.avatar : undefined;
  mkdirSync(SKINS_DIR, { recursive: true });
  for (const [variant, png] of Object.entries(files)) writeFileSync(skinFile(account.uuid, variant as '' | '.wide' | '.slim'), png);
  saveAccountsFile((file) => {
    const stored = file.accounts.find((a) => a.id === account.id);
    if (!stored) return;
    stored.skin = { slim: Boolean(upload.slimModel) };
    stored.avatar = avatar;
  });
  cache.delete(`offline:${account.uuid}`);
  return accountsState();
}

export function removeSkin(): AccountsState {
  const account = offlineSelected();
  for (const variant of ['', '.wide', '.slim'] as const) rmSync(skinFile(account.uuid, variant), { force: true });
  saveAccountsFile((file) => {
    const stored = file.accounts.find((a) => a.id === account.id);
    if (!stored) return;
    delete stored.skin;
    delete stored.avatar;
  });
  cache.delete(`offline:${account.uuid}`);
  return accountsState();
}

/** Skin images for the in-game skin pack of this account (null = no own skin). */
export function skinFilesFor(account: StoredAccount | null): SkinFiles | null {
  if (account?.type !== 'offline' || !account.skin) return null;
  try {
    return { wide: readFileSync(skinFile(account.uuid, '.wide')), slim: readFileSync(skinFile(account.uuid, '.slim')) };
  } catch {
    return null;
  }
}
