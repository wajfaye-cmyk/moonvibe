// Installiert eine Minecraft-Version: Versions-JSON, Java, Client, Bibliotheken, Assets, Natives.
import AdmZip from 'adm-zip';
import { existsSync } from 'node:fs';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { LocalizedError } from '../shared/i18n';
import type { Loader, Progress } from '../shared/types';
import { downloadAll, downloadFile, fetchJson, type DownloadTask } from './http';
import { ensureJava } from './java';
import { is64Bit, osName, rulesAllow } from './rules';
import { getLoaderProfile, getVanillaJson, mergeProfile, withDefaultJvmArgs, type Library, type VersionJson } from './versions';

export interface Installation {
  version: VersionJson;
  javaPath: string;
  classpath: string[];
  nativesDir: string;
  assetsDir: string;
  librariesDir: string;
  logConfigArg: string | null;
}

export type ProgressFn = (p: Progress) => void;

/** "gruppe:artefakt:version[:classifier][@ext]" → Pfad im Maven-Repository */
export function mavenPath(name: string): string {
  const [coords, ext = 'jar'] = name.split('@');
  const [group, artifact, version, classifier] = coords.split(':');
  const file = `${artifact}-${version}${classifier ? `-${classifier}` : ''}.${ext}`;
  return `${group.replace(/\./g, '/')}/${artifact}/${version}/${file}`;
}

interface ResolvedLibrary {
  task: DownloadTask | null;
  path: string;
  natives: boolean;
  exclude: string[];
}

function resolveLibrary(lib: Library, librariesDir: string): ResolvedLibrary | null {
  if (!rulesAllow(lib.rules)) return null;
  const repo = (lib.url ?? 'https://libraries.minecraft.net/').replace(/\/?$/, '/');

  if (lib.natives) {
    const classifier = lib.natives[osName()]?.replace('${arch}', is64Bit() ? '64' : '32');
    if (!classifier) return null;
    const art = lib.downloads?.classifiers?.[classifier];
    const rel = art?.path ?? mavenPath(`${lib.name}:${classifier}`);
    const path = join(librariesDir, rel);
    return {
      task: { url: art?.url ?? repo + rel, path, sha1: art?.sha1, size: art?.size },
      path,
      natives: true,
      exclude: lib.extract?.exclude ?? [],
    };
  }

  const art = lib.downloads?.artifact;
  const rel = art?.path ?? mavenPath(lib.name);
  const path = join(librariesDir, rel);
  const url = art ? art.url : repo + rel;
  return {
    task: url ? { url, path, sha1: art?.sha1 ?? lib.sha1, size: art?.size ?? lib.size } : null,
    path,
    natives: false,
    exclude: [],
  };
}

async function extractNatives(jars: ResolvedLibrary[], nativesDir: string): Promise<void> {
  await mkdir(nativesDir, { recursive: true });
  for (const jar of jars) {
    const zip = new AdmZip(jar.path);
    for (const entry of zip.getEntries()) {
      if (entry.isDirectory) continue;
      if (jar.exclude.some((ex) => entry.entryName.startsWith(ex))) continue;
      const target = join(nativesDir, entry.entryName);
      if (existsSync(target)) continue;
      await mkdir(dirname(target), { recursive: true });
      await writeFile(target, entry.getData());
    }
  }
}

async function installAssets(version: VersionJson, assetsDir: string, progress: ProgressFn): Promise<void> {
  const index = version.assetIndex;
  if (!index) return;
  const indexFile = join(assetsDir, 'indexes', `${index.id}.json`);
  let objects: Record<string, { hash: string; size: number }>;
  try {
    if (!existsSync(indexFile)) await downloadFile({ url: index.url, path: indexFile, sha1: index.sha1 });
    objects = JSON.parse(await readFile(indexFile, 'utf8')).objects;
  } catch {
    objects = (await fetchJson<{ objects: typeof objects }>(index.url)).objects;
  }
  const tasks = Object.values(objects).map(({ hash, size }) => ({
    url: `https://resources.download.minecraft.net/${hash.slice(0, 2)}/${hash}`,
    path: join(assetsDir, 'objects', hash.slice(0, 2), hash),
    sha1: hash,
    size,
  }));
  await downloadAll(tasks, (done, total) => progress({ stage: 'stage.assets', done, total }));
}

/** Mojangs Java-Laufzeiten mit ihrer Hauptversion (aufsteigend). */
const JAVA_RUNTIMES: [number, string][] = [
  [8, 'jre-legacy'],
  [16, 'java-runtime-alpha'],
  [17, 'java-runtime-gamma'],
  [21, 'java-runtime-delta'],
  [25, 'java-runtime-epsilon'],
];

/** Java der Version – oder eine neuere, wenn eine Mod das verlangt (z. B. moonvibe auf 1.16.5 → Java 17). */
function javaComponent(vanilla: VersionJson, minJava: number): string {
  const own = vanilla.javaVersion ?? { component: 'jre-legacy', majorVersion: 8 };
  if (own.majorVersion >= minJava) return own.component;
  return JAVA_RUNTIMES.find(([major]) => major >= minJava)?.[1] ?? own.component;
}

export async function installVersion(
  dataDir: string,
  mcVersion: string,
  loader: Loader | null,
  progress: ProgressFn,
  minJava = 0,
): Promise<Installation> {
  progress({ stage: 'stage.version', done: 0, total: 1 });
  const vanilla = await getVanillaJson(dataDir, mcVersion);
  const version = loader ? mergeProfile(vanilla, await getLoaderProfile(dataDir, loader, mcVersion)) : withDefaultJvmArgs(vanilla);

  const librariesDir = join(dataDir, 'libraries');
  const assetsDir = join(dataDir, 'assets');
  const versionDir = join(dataDir, 'versions', mcVersion);
  const nativesDir = join(versionDir, 'natives');

  const component = javaComponent(vanilla, minJava);
  const javaPath = await ensureJava(dataDir, component, (done, total) => progress({ stage: 'stage.java', done, total }));

  // Client-Jar + Bibliotheken
  const tasks: DownloadTask[] = [];
  const clientJar = join(versionDir, `${mcVersion}.jar`);
  const client = vanilla.downloads?.client;
  if (!client) throw new LocalizedError('error.noClient', { version: mcVersion });
  tasks.push({ url: client.url, path: clientJar, sha1: client.sha1, size: client.size });

  const libs = version.libraries.map((l) => resolveLibrary(l, librariesDir)).filter((l): l is ResolvedLibrary => l !== null);
  for (const lib of libs) if (lib.task) tasks.push(lib.task);
  await downloadAll(tasks, (done, total) => progress({ stage: 'stage.libraries', done, total }));

  await extractNatives(
    libs.filter((l) => l.natives),
    nativesDir,
  );

  await installAssets(version, assetsDir, progress);

  let logConfigArg: string | null = null;
  const logging = vanilla.logging?.client;
  if (logging) {
    const file = join(assetsDir, 'log_configs', logging.file.id);
    await downloadAll([{ url: logging.file.url, path: file, sha1: logging.file.sha1, size: logging.file.size }]);
    logConfigArg = logging.argument.replace('${path}', file);
  }

  const classpath = [...new Set(libs.filter((l) => !l.natives).map((l) => l.path)), clientJar];
  return { version, javaPath, classpath, nativesDir, assetsDir, librariesDir, logConfigArg };
}
