// Verbindung zum moonvibe-Cosmetics-Server (siehe Ordner backend/).
import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { LocalizedError, translate } from '../shared/i18n';
import type { Cosmetic, CosmeticSlot, CosmeticsState, Equipment, NewsItem, Rarity } from '../shared/types';
import { fetchJson, HttpError, postJson } from '../core/http';
import { joinSession } from '../core/microsoft';
import { selectedAccount, validMinecraftToken } from './accounts';
import { apiUrl as api, DEV_MODE, getAccountsFile, getSettings, type StoredAccount } from './store';

const t = (key: Parameters<typeof translate>[1]) => translate(getSettings().language, key);
const NOTHING: Equipment = { cape: null, hat: null, face: null, back: null, wings: null, shoes: null, pet: null };

/** Antwort des Servers je Spieler: Auswahl + ggf. eigenes Cape */
type PlayerEntry = Equipment & { customCape?: string | null };

interface CatalogEntry {
  id: string;
  type: CosmeticSlot;
  rarity?: Rarity;
  names: Cosmetic['names'];
  texture: string;
  model: string | null;
  thumbnail: string | null;
}

/** moonvibe-Sitzungen je Account und Server-Adresse (gelten 30 Tage, hier nur bis zum Beenden des Launchers gemerkt). */
const sessions = new Map<string, { token: string; expiresAt: number }>();

/**
 * Sitzung beim Cosmetics-Server. Der Minecraft-Token geht dabei nur an Mojang: Wir melden uns dort mit einer
 * Kennung vom moonvibe-Server an, und der fragt bei Mojang nach, wer das war.
 */
export async function cosmeticsSession(account: StoredAccount): Promise<string> {
  const key = `${account.id}@${api()}`;
  const cached = sessions.get(key);
  if (cached && cached.expiresAt - Date.now() > 24 * 60 * 60 * 1000) return cached.token;
  const accessToken = await validMinecraftToken(account);
  // nach einer Token-Erneuerung stehen Name/UUID frisch in der Account-Datei
  const fresh = getAccountsFile().accounts.find((a) => a.id === account.id) ?? account;
  const { serverId } = await postJson<{ serverId: string }>(`${api()}/api/auth/challenge`, {});
  await joinSession(accessToken, fresh.uuid, serverId);
  const session = await postJson<{ token: string; expiresAt: number }>(`${api()}/api/auth/session`, { name: fresh.name, serverId });
  sessions.set(key, session);
  return session.token;
}

async function authHeaders(): Promise<Record<string, string>> {
  const account = selectedAccount();
  if (!account) throw new LocalizedError('cosmetics.loginRequired');
  if (account.type === 'offline') {
    // Nur zum Testen: der Server akzeptiert das nur, wenn er im Testmodus läuft.
    if (!DEV_MODE) throw new LocalizedError('cosmetics.microsoftOnly');
    return { 'X-Dev-Uuid': account.uuid };
  }
  return { Authorization: `Bearer ${await cosmeticsSession(account)}` };
}

export async function cosmeticsState(): Promise<CosmeticsState> {
  if (!api()) return { online: false, catalog: [], equipped: NOTHING, customCape: null, error: t('cosmetics.noServer') };
  let catalog: Cosmetic[];
  try {
    const list = await fetchJson<CatalogEntry[]>(`${api()}/api/cosmetics`);
    catalog = list.map((c) => ({
      id: c.id,
      type: c.type,
      rarity: c.rarity ?? 'common',
      names: c.names ?? {},
      textureUrl: api() + c.texture,
      modelUrl: c.model ? api() + c.model : null,
      thumbnailUrl: c.thumbnail ? api() + c.thumbnail : null,
    }));
  } catch {
    return { online: false, catalog: [], equipped: NOTHING, customCape: null, error: t('cosmetics.offline') };
  }

  const account = selectedAccount();
  if (!account) return { online: true, catalog, equipped: NOTHING, customCape: null, error: t('cosmetics.loginRequired') };

  const players = await fetchJson<Record<string, PlayerEntry>>(`${api()}/api/players?uuids=${account.uuid}`).catch(
    () => ({}) as Record<string, PlayerEntry>,
  );
  const { customCape = null, ...slots } = players[account.uuid] ?? NOTHING;
  if (customCape) {
    // eigenes Cape erscheint als erstes Cape im Katalog (Name übersetzt die Oberfläche)
    catalog.unshift({
      id: customCape,
      type: 'cape',
      rarity: 'legendary',
      names: {},
      textureUrl: `${api()}/textures/capes/${customCape}.png`,
      modelUrl: null,
      thumbnailUrl: null,
      custom: true,
    });
  }
  const error = account.type === 'offline' && !DEV_MODE ? t('cosmetics.microsoftOnly') : null;
  return { online: true, catalog, equipped: { ...NOTHING, ...slots }, customCape, error };
}

/** 3D-Modell eines Cosmetics (JSON) – über den Hauptprozess, weil die Oberfläche keine fremden Server abfragen darf. */
export async function cosmeticModel(id: string): Promise<unknown> {
  if (!/^[a-z0-9_]+$/.test(id)) throw new Error('invalid id');
  return fetchJson(`${api()}/models/${id}.json`);
}

export async function equip(slot: CosmeticSlot, id: string | null): Promise<CosmeticsState> {
  await send('/api/me', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ [slot]: id }) });
  return cosmeticsState();
}

/** Fertiges Cape-PNG (2:1) hochladen – der Server zieht es direkt an. */
export async function uploadCape(png: Buffer): Promise<CosmeticsState> {
  await send('/api/me/cape', { method: 'PUT', headers: { 'Content-Type': 'image/png' }, body: png });
  return cosmeticsState();
}

export async function removeCustomCape(): Promise<CosmeticsState> {
  await send('/api/me/cape', { method: 'DELETE' });
  return cosmeticsState();
}

async function send(path: string, init: RequestInit, retry = true): Promise<void> {
  try {
    await fetchJson(`${api()}${path}`, { ...init, headers: { ...(init.headers as Record<string, string>), ...(await authHeaders()) } });
  } catch (e) {
    // Sitzung abgelaufen (z. B. Server neu aufgesetzt) → einmal neu anmelden
    if (e instanceof HttpError && e.status === 401 && retry && sessions.size) {
      sessions.clear();
      return send(path, init, false);
    }
    if (e instanceof HttpError) throw new LocalizedError('error.server', { details: serverMessage(e) });
    throw e;
  }
}

/** Fehlermeldung aus der Server-Antwort ({"error": "..."}) – sonst Statuscode. */
function serverMessage(e: HttpError): string {
  try {
    return (JSON.parse(e.body) as { error?: string }).error ?? String(e.status);
  } catch {
    return e.body || String(e.status);
  }
}

export async function news(): Promise<NewsItem[]> {
  if (!api()) return bundledNews();
  return fetchJson<NewsItem[]>(`${api()}/api/news`).catch(() => bundledNews());
}

/** Ohne Server: die mitgelieferten News (Stand beim Bauen des Launchers). */
async function bundledNews(): Promise<NewsItem[]> {
  try {
    return JSON.parse(await readFile(join(__dirname, '..', 'renderer', 'assets', 'news', 'news.json'), 'utf8')) as NewsItem[];
  } catch {
    return [];
  }
}
