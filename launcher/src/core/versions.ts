// Versionsliste von Mojang + Fabric/Legacy Fabric und das Zusammenführen der Versions-JSONs.
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import type { GameVersion, Loader } from '../shared/types';
import { LocalizedError } from '../shared/i18n';
import { fetchJson } from './http';
import type { Rule } from './rules';

const MANIFEST_URL = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const LOADER_META: Record<Loader, string> = {
  fabric: 'https://meta.fabricmc.net/v2',
  'legacy-fabric': 'https://meta.legacyfabric.net/v2',
};
/** Ältere Versionen nutzen ein anderes Asset-System; 1.8 ist die älteste, die wir anbieten. */
const OLDEST_RELEASE = '2014-09-02';

export type Argument = string | { rules?: Rule[]; value: string | string[] };

export interface Artifact {
  path?: string;
  url: string;
  sha1?: string;
  size?: number;
}

export interface Library {
  name: string;
  url?: string;
  sha1?: string;
  size?: number;
  downloads?: { artifact?: Artifact; classifiers?: Record<string, Artifact> };
  natives?: Partial<Record<'windows' | 'osx' | 'linux', string>>;
  extract?: { exclude?: string[] };
  rules?: Rule[];
}

export interface VersionJson {
  id: string;
  inheritsFrom?: string;
  type?: string;
  mainClass: string;
  minecraftArguments?: string;
  arguments?: { game?: Argument[]; jvm?: Argument[] };
  libraries: Library[];
  assetIndex?: { id: string; url: string; sha1: string; size: number; totalSize?: number };
  assets?: string;
  downloads?: { client?: Artifact };
  javaVersion?: { component: string; majorVersion: number };
  logging?: { client?: { argument: string; file: { id: string; url: string; sha1: string; size: number } } };
}

interface ManifestEntry {
  id: string;
  type: string;
  url: string;
  sha1: string;
  releaseTime: string;
}

let manifestCache: { versions: ManifestEntry[] } | null = null;

/** JSON aus dem Netz laden und auf der Platte merken – ohne Internet gilt die gemerkte Fassung. */
async function cachedJson<T>(url: string, file: string): Promise<T> {
  try {
    const json = await fetchJson<T>(url);
    await mkdir(dirname(file), { recursive: true });
    await writeFile(file, JSON.stringify(json));
    return json;
  } catch (e) {
    const cached = await readCached<T>(file);
    if (cached) return cached;
    throw e;
  }
}

async function getManifest(dataDir: string): Promise<{ versions: ManifestEntry[] }> {
  manifestCache ??= await cachedJson<{ versions: ManifestEntry[] }>(MANIFEST_URL, join(dataDir, 'versions', 'version_manifest_v2.json'));
  return manifestCache;
}

/**
 * Versionen, die der Loader unterstützt. Ohne Antwort (und ohne gemerkte Liste) lieber einen Fehler melden,
 * als Minecraft still ohne Fabric – und damit ohne moonvibe – zu starten.
 */
async function stableGameVersions(dataDir: string, loader: Loader): Promise<Set<string>> {
  const list = await cachedJson<{ version: string; stable: boolean }[]>(
    `${LOADER_META[loader]}/versions/game`,
    join(dataDir, 'versions', `${loader}-game-versions.json`),
  );
  return new Set(list.filter((v) => v.stable).map((v) => v.version));
}

/** Alle spielbaren Release-Versionen, neueste zuerst. */
export async function listGameVersions(dataDir: string, moonvibeVersions: Set<string>): Promise<GameVersion[]> {
  const [manifest, fabric, legacy] = await Promise.all([
    getManifest(dataDir),
    stableGameVersions(dataDir, 'fabric'),
    stableGameVersions(dataDir, 'legacy-fabric'),
  ]);
  return manifest.versions
    .filter((v) => v.type === 'release' && v.releaseTime >= OLDEST_RELEASE)
    .map((v) => ({
      id: v.id,
      releaseTime: v.releaseTime,
      loader: fabric.has(v.id) ? 'fabric' : legacy.has(v.id) ? 'legacy-fabric' : null,
      moonvibe: moonvibeVersions.has(v.id),
    }));
}

async function readCached<T>(file: string): Promise<T | null> {
  try {
    return JSON.parse(await readFile(file, 'utf8')) as T;
  } catch {
    return null;
  }
}

/** Lädt die Versions-JSON von Mojang (oder aus dem Cache, wenn man offline ist). */
export async function getVanillaJson(dataDir: string, id: string): Promise<VersionJson> {
  const file = join(dataDir, 'versions', id, `${id}.json`);
  try {
    const entry = (await getManifest(dataDir)).versions.find((v) => v.id === id);
    if (!entry) throw new LocalizedError('error.unknownVersion', { version: id });
    const json = await fetchJson<VersionJson>(entry.url);
    await mkdir(join(dataDir, 'versions', id), { recursive: true });
    await writeFile(file, JSON.stringify(json));
    return json;
  } catch (e) {
    const cached = await readCached<VersionJson>(file);
    if (cached) return cached;
    throw e;
  }
}

/** Lädt das Loader-Profil (Fabric/Legacy Fabric) mit dem neuesten stabilen Loader. */
export async function getLoaderProfile(dataDir: string, loader: Loader, mc: string): Promise<VersionJson> {
  const file = join(dataDir, 'versions', `${loader}-${mc}`, 'profile.json');
  try {
    const base = LOADER_META[loader];
    const loaders = await fetchJson<{ loader: { version: string; stable: boolean } }[]>(`${base}/versions/loader/${mc}`);
    const best = loaders.find((l) => l.loader.stable) ?? loaders[0];
    if (!best) throw new LocalizedError('error.loaderUnsupported', { loader, version: mc });
    const json = await fetchJson<VersionJson>(`${base}/versions/loader/${mc}/${best.loader.version}/profile/json`);
    await mkdir(join(dataDir, 'versions', `${loader}-${mc}`), { recursive: true });
    await writeFile(file, JSON.stringify(json));
    return json;
  } catch (e) {
    const cached = await readCached<VersionJson>(file);
    if (cached) return cached;
    throw e;
  }
}

/** "gruppe:artefakt[:classifier]" – damit erkennen wir doppelte Bibliotheken in verschiedenen Versionen. */
function libraryKey(name: string): string {
  const [group, artifact, , classifier] = name.split('@')[0].split(':');
  return classifier ? `${group}:${artifact}:${classifier}` : `${group}:${artifact}`;
}

/**
 * Führt Loader-Profil (Kind) und Vanilla-Version (Eltern) zusammen.
 * Bibliotheken des Loaders haben Vorrang (z. B. ersetzt Legacy Fabric LWJGL 2).
 */
export function mergeProfile(parent: VersionJson, child: VersionJson): VersionJson {
  const base = withDefaultJvmArgs(parent);
  const childKeys = new Set(child.libraries.map((l) => libraryKey(l.name)));
  const libraries = [...child.libraries, ...base.libraries.filter((l) => !childKeys.has(libraryKey(l.name)))];

  return {
    ...base,
    libraries,
    mainClass: child.mainClass ?? base.mainClass,
    minecraftArguments: child.minecraftArguments ?? base.minecraftArguments,
    arguments: {
      game: [...(base.arguments?.game ?? []), ...(child.arguments?.game ?? [])],
      jvm: [...(base.arguments?.jvm ?? []), ...(child.arguments?.jvm ?? [])],
    },
  };
}

/** Versionen vor 1.13 bringen keine JVM-Argumente mit – dann nehmen wir die Standard-Argumente des offiziellen Launchers. */
export function withDefaultJvmArgs(version: VersionJson): VersionJson {
  if (version.arguments?.jvm) return version;
  return {
    ...version,
    arguments: {
      game: version.arguments?.game ?? [],
      jvm: [
        { rules: [{ action: 'allow', os: { name: 'windows' } }], value: '-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump' },
        '-Djava.library.path=${natives_directory}',
        '-Dminecraft.launcher.brand=${launcher_name}',
        '-Dminecraft.launcher.version=${launcher_version}',
        '-cp',
        '${classpath}',
      ],
    },
  };
}
