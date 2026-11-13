// Lädt die passende Java-Version direkt von Mojang – Spieler müssen kein Java installieren.
import { existsSync } from 'node:fs';
import { chmod, mkdir, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { LocalizedError } from '../shared/i18n';
import { downloadAll, fetchJson, type DownloadTask } from './http';
import { osName } from './rules';

const RUNTIMES_URL =
  'https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json';

type RuntimeIndex = Record<string, Record<string, { manifest: { url: string; sha1: string } }[]>>;

interface RuntimeManifest {
  files: Record<
    string,
    { type: 'file' | 'directory' | 'link'; executable?: boolean; downloads?: { raw: { url: string; sha1: string; size: number } } }
  >;
}

function platformKey(): string {
  const arm = process.arch === 'arm64';
  switch (osName()) {
    case 'windows':
      return arm ? 'windows-arm64' : process.arch === 'ia32' ? 'windows-x86' : 'windows-x64';
    case 'osx':
      return arm ? 'mac-os-arm64' : 'mac-os';
    default:
      return process.arch === 'ia32' ? 'linux-i386' : 'linux';
  }
}

export function javaExecutable(home: string): string {
  if (osName() === 'windows') return join(home, 'bin', 'javaw.exe');
  if (osName() === 'osx') return join(home, 'jre.bundle', 'Contents', 'Home', 'bin', 'java');
  return join(home, 'bin', 'java');
}

/**
 * Stellt sicher, dass die Java-Laufzeit "component" (z. B. java-runtime-epsilon) installiert ist,
 * und gibt den Pfad zur java-Datei zurück.
 */
export async function ensureJava(
  dataDir: string,
  component: string,
  onProgress?: (done: number, total: number) => void,
): Promise<string> {
  const home = join(dataDir, 'runtime', component);
  const marker = join(home, '.moonvibe-installed');
  const exe = javaExecutable(home);

  let index: RuntimeIndex;
  try {
    index = await fetchJson<RuntimeIndex>(RUNTIMES_URL);
  } catch (e) {
    // Offline: vorhandene Installation nutzen.
    if (existsSync(exe)) return exe;
    throw e;
  }

  const entry = index[platformKey()]?.[component]?.[0];
  if (!entry) throw new LocalizedError('error.javaUnavailable', { component });

  const installed = await readFile(marker, 'utf8').catch(() => '');
  if (installed === entry.manifest.sha1 && existsSync(exe)) return exe;

  const manifest = await fetchJson<RuntimeManifest>(entry.manifest.url);
  const tasks: DownloadTask[] = [];
  const executables: string[] = [];
  for (const [path, file] of Object.entries(manifest.files)) {
    const target = join(home, path);
    if (file.type === 'directory') {
      await mkdir(target, { recursive: true });
    } else if (file.type === 'file' && file.downloads) {
      tasks.push({ url: file.downloads.raw.url, path: target, sha1: file.downloads.raw.sha1, size: file.downloads.raw.size });
      if (file.executable) executables.push(target);
    }
  }
  await downloadAll(tasks, onProgress);
  if (osName() !== 'windows') {
    await Promise.all(executables.map((f) => chmod(f, 0o755)));
  }
  await writeFile(marker, entry.manifest.sha1);
  return exe;
}
