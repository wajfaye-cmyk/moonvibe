// Shows the uploaded skin of a username account in game – without touching the mod:
// the launcher writes a small resource pack into the instance that replaces Minecraft's
// default skins (the ones players without a Mojang skin get) with the uploaded skin.
// Works from 1.8.9 to the newest versions; only the player themself sees it.
import AdmZip from 'adm-zip';
import { existsSync } from 'node:fs';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

export const SKIN_PACK_FILE = 'moonvibe-skin.zip';

/** Default skins since 1.19.3 (each exists with classic and with slim arms). */
const DEFAULT_SKINS = ['steve', 'alex', 'ari', 'efe', 'kai', 'makena', 'noor', 'sunny', 'zuri'];

/** pack_format by release date of the Minecraft version (first version that uses the format). */
const PACK_FORMATS: [since: string, format: number][] = [
  ['2025-06-30', 64],
  ['2025-06-17', 63],
  ['2025-03-25', 55],
  ['2024-12-03', 46],
  ['2024-10-22', 42],
  ['2024-06-13', 34],
  ['2024-04-23', 32],
  ['2023-12-05', 22],
  ['2023-09-21', 18],
  ['2023-06-07', 15],
  ['2023-03-14', 13],
  ['2022-12-07', 12],
  ['2022-06-07', 9],
  ['2021-11-30', 8],
  ['2021-06-08', 7],
  ['2020-08-11', 6],
  ['2019-12-10', 5],
  ['2018-07-18', 4],
  ['2016-11-14', 3],
  ['2016-02-29', 2],
];
/** From 1.21.9 on, packs declare a range (min_format/max_format) instead of pack_format. */
const RANGE_SINCE = '2025-09-30';
/** From 1.13 on, packs in the folder are called "file/<name>" in options.txt. */
const FILE_PREFIX_SINCE = '2018-07-18';

export function packMeta(releaseTime: string): string {
  const description = 'Moonvibe: your uploaded skin';
  const day = releaseTime.slice(0, 10);
  if (day >= RANGE_SINCE) return JSON.stringify({ pack: { description, min_format: 65, max_format: 9999 } }, null, 2);
  const format = PACK_FORMATS.find(([since]) => day >= since)?.[1] ?? 1;
  return JSON.stringify({ pack: { description, pack_format: format } }, null, 2);
}

export interface SkinFiles {
  /** 64×64 PNG for the classic model (4 px arms) */
  wide: Buffer;
  /** 64×64 PNG for the slim model (3 px arms) */
  slim: Buffer;
}

function buildPack(skin: SkinFiles, releaseTime: string): Buffer {
  const zip = new AdmZip();
  zip.addFile('pack.mcmeta', Buffer.from(packMeta(releaseTime)));
  const base = 'assets/minecraft/textures/entity/';
  // up to 1.19.2
  zip.addFile(`${base}steve.png`, skin.wide);
  zip.addFile(`${base}alex.png`, skin.slim);
  // 1.19.3 and newer
  for (const name of DEFAULT_SKINS) {
    zip.addFile(`${base}player/wide/${name}.png`, skin.wide);
    zip.addFile(`${base}player/slim/${name}.png`, skin.slim);
  }
  return zip.toBuffer();
}

/** Adds or removes one entry of a JSON list line ("key:[...]") in options.txt. */
export function editListOption(options: string, key: string, entry: string, present: boolean): string {
  const lines = options.split(/\r?\n/);
  const index = lines.findIndex((l) => l.startsWith(`${key}:`));
  let list: string[] = [];
  if (index >= 0) {
    try {
      const parsed: unknown = JSON.parse(lines[index]!.slice(key.length + 1));
      if (Array.isArray(parsed)) list = parsed.filter((x): x is string => typeof x === 'string');
    } catch {
      // unreadable line → start with an empty list
    }
  }
  const without = list.filter((x) => x !== entry);
  const next = present ? [...without, entry] : without;
  if (index < 0 && !present) return options;
  if (next.length === list.length && next.every((x, i) => x === list[i])) return options;
  const line = `${key}:${JSON.stringify(next)}`;
  if (index >= 0) lines[index] = line;
  else {
    // keep a trailing empty line at the end of the file
    const last = lines.length && lines[lines.length - 1] === '' ? lines.length - 1 : lines.length;
    lines.splice(last, 0, line);
  }
  return lines.join('\n');
}

/**
 * Writes (skin given) or removes (skin = null) the skin pack of an instance and switches it on/off
 * in options.txt. Other resource packs and their order stay untouched.
 */
export async function applySkinPack(instanceDir: string, releaseTime: string, skin: SkinFiles | null): Promise<void> {
  const packs = join(instanceDir, 'resourcepacks');
  const file = join(packs, SKIN_PACK_FILE);
  const optionsFile = join(instanceDir, 'options.txt');
  const entry = releaseTime.slice(0, 10) >= FILE_PREFIX_SINCE ? `file/${SKIN_PACK_FILE}` : SKIN_PACK_FILE;

  if (skin) {
    await mkdir(packs, { recursive: true });
    const pack = buildPack(skin, releaseTime);
    const old = await readFile(file).catch(() => null);
    if (!old || !old.equals(pack)) await writeFile(file, pack);
  } else {
    await rm(file, { force: true });
  }

  if (!existsSync(optionsFile) && !skin) return;
  const before = await readFile(optionsFile, 'utf8').catch(() => '');
  let after = editListOption(before, 'resourcePacks', entry, Boolean(skin));
  // Also listed as "incompatible", so Minecraft loads it even if it thinks the pack is for another version.
  after = editListOption(after, 'incompatibleResourcePacks', entry, Boolean(skin));
  if (after !== before) await writeFile(optionsFile, after);
}
