// Das Minecraft-Hauptmenü-Panorama als bewegter Hintergrund im Launcher.
// Die Bilder kommen aus den Minecraft-Assets (lokal installiert oder direkt von Mojang geladen).
import { existsSync, readdirSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { downloadAll, fetchJson } from '../core/http';
import type { VersionJson } from '../core/versions';
import { DATA_DIR } from './store';

const KEYS = [0, 1, 2, 3].map((i) => `minecraft/textures/gui/title/background/panorama_${i}.png`);
const MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
let cached: string[] | null = null;

type AssetIndex = { objects: Record<string, { hash: string; size: number }> };

async function objectsFromIndex(index: AssetIndex): Promise<{ hash: string; size: number }[] | null> {
  const objects = KEYS.map((k) => index.objects[k]);
  return objects.every(Boolean) ? objects : null;
}

async function localIndex(): Promise<AssetIndex | null> {
  const dir = join(DATA_DIR, 'assets', 'indexes');
  if (!existsSync(dir)) return null;
  // neueste Indizes zuerst (höhere Nummern = neuere Versionen)
  const files = readdirSync(dir).sort((a, b) => parseFloat(b) - parseFloat(a));
  for (const file of files) {
    try {
      const index = JSON.parse(await readFile(join(dir, file), 'utf8')) as AssetIndex;
      if (await objectsFromIndex(index)) return index;
    } catch {
      // kaputte Datei überspringen
    }
  }
  return null;
}

async function remoteIndex(): Promise<AssetIndex> {
  const manifest = await fetchJson<{ latest: { release: string }; versions: { id: string; url: string }[] }>(MANIFEST);
  const entry = manifest.versions.find((v) => v.id === manifest.latest.release)!;
  const version = await fetchJson<VersionJson>(entry.url);
  return fetchJson<AssetIndex>(version.assetIndex!.url);
}

export async function panoramaImages(): Promise<string[]> {
  if (cached) return cached;
  const index = (await localIndex()) ?? (await remoteIndex());
  const objects = (await objectsFromIndex(index)) ?? [];
  const paths = objects.map((o) => join(DATA_DIR, 'assets', 'objects', o.hash.slice(0, 2), o.hash));
  await downloadAll(
    objects.map((o, i) => ({
      url: `https://resources.download.minecraft.net/${o.hash.slice(0, 2)}/${o.hash}`,
      path: paths[i],
      sha1: o.hash,
      size: o.size,
    })),
  );
  cached = await Promise.all(paths.map(async (p) => `data:image/png;base64,${(await readFile(p)).toString('base64')}`));
  return cached;
}
