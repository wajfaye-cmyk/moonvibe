// Netzwerk-Hilfen: JSON laden und Dateien parallel herunterladen (mit SHA-1-Prüfung).
import { createHash } from 'node:crypto';
import { createWriteStream } from 'node:fs';
import { mkdir, rename, rm, stat } from 'node:fs/promises';
import { dirname } from 'node:path';
import { Readable, Transform } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { LocalizedError } from '../shared/i18n';

export const LAUNCHER_VERSION = '0.2.0';
export const USER_AGENT = `moonvibe-launcher/${LAUNCHER_VERSION} (github.com/nexorateam/moonvibe)`;

export class HttpError extends LocalizedError {
  constructor(
    public readonly status: number,
    public readonly url: string,
    public readonly body: string,
  ) {
    super('error.http', { status, url });
  }
}

async function withRetries<T>(fn: () => Promise<T>, attempts = 3): Promise<T> {
  let lastError: unknown;
  for (let i = 0; i < attempts; i++) {
    try {
      return await fn();
    } catch (e) {
      lastError = e;
      // Client-Fehler (4xx) wiederholen bringt nichts.
      if (e instanceof HttpError && e.status >= 400 && e.status < 500) throw e;
      await new Promise((r) => setTimeout(r, 500 * (i + 1)));
    }
  }
  throw lastError;
}

export async function fetchJson<T>(url: string, init: RequestInit = {}): Promise<T> {
  return withRetries(async () => {
    const res = await fetch(url, {
      ...init,
      headers: { 'User-Agent': USER_AGENT, Accept: 'application/json', ...(init.headers ?? {}) },
      // ein hängender Server soll den Launcher nicht ewig warten lassen
      signal: init.signal ?? AbortSignal.timeout(20_000),
    });
    if (!res.ok) throw new HttpError(res.status, url, await res.text().catch(() => ''));
    return (await res.json()) as T;
  });
}

export async function postJson<T>(url: string, body: unknown, headers: Record<string, string> = {}): Promise<T> {
  return fetchJson<T>(url, {
    method: 'POST',
    body: JSON.stringify(body),
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

export interface DownloadTask {
  url: string;
  path: string;
  sha1?: string;
  size?: number;
}

async function fileMatches(task: DownloadTask): Promise<boolean> {
  try {
    const s = await stat(task.path);
    return task.size === undefined ? s.size > 0 : s.size === task.size;
  } catch {
    return false;
  }
}

export async function downloadFile(task: DownloadTask): Promise<void> {
  await withRetries(async () => {
    await mkdir(dirname(task.path), { recursive: true });
    const tmp = `${task.path}.part`;
    const res = await fetch(task.url, { headers: { 'User-Agent': USER_AGENT } });
    if (!res.ok || !res.body) throw new HttpError(res.status, task.url, '');

    const hash = createHash('sha1');
    const hasher = new Transform({
      transform(chunk, _enc, cb) {
        hash.update(chunk);
        cb(null, chunk);
      },
    });
    try {
      await pipeline(Readable.fromWeb(res.body as import('node:stream/web').ReadableStream), hasher, createWriteStream(tmp));
      const digest = hash.digest('hex');
      if (task.sha1 && digest !== task.sha1.toLowerCase()) {
        throw new LocalizedError('error.checksum', { url: task.url });
      }
      await rename(tmp, task.path);
    } catch (e) {
      await rm(tmp, { force: true });
      throw e;
    }
  });
}

/**
 * Lädt alle fehlenden Dateien parallel herunter. Bereits vorhandene Dateien
 * mit passender Größe werden übersprungen.
 */
export async function downloadAll(
  tasks: DownloadTask[],
  onProgress?: (done: number, total: number) => void,
  concurrency = 16,
): Promise<void> {
  // Doppelte Ziele (kommt bei Bibliotheken vor) nur einmal laden.
  const unique = [...new Map(tasks.map((t) => [t.path, t])).values()];
  const missing: DownloadTask[] = [];
  await Promise.all(
    unique.map(async (t) => {
      if (!(await fileMatches(t))) missing.push(t);
    }),
  );

  let done = 0;
  const total = missing.length;
  onProgress?.(0, total);
  let index = 0;
  const worker = async () => {
    while (index < missing.length) {
      const task = missing[index++];
      await downloadFile(task);
      done++;
      onProgress?.(done, total);
    }
  };
  await Promise.all(Array.from({ length: Math.min(concurrency, total) }, worker));
}
