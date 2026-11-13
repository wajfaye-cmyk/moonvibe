// Skin des angemeldeten Spielers für die 3D-Vorschau im Launcher.
import AdmZip from 'adm-zip';
import { readdirSync } from 'node:fs';
import { join } from 'node:path';
import type { SkinInfo } from '../shared/types';
import { fetchJson, USER_AGENT } from '../core/http';
import { selectedAccount } from './accounts';
import { DATA_DIR } from './store';

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
  info ??= defaultSkin();
  if (info.dataUrl) cache.set(key, info);
  return info;
}
