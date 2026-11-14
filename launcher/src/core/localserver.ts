// Built-in cosmetics server: runs inside the launcher on 127.0.0.1, so cosmetics work right after
// installing – without hosting anything. It speaks the same API as the hosted server (backend/),
// but stores everything on this PC. Players are identified by their UUID (X-Dev-Uuid header);
// that is fine here because the server only listens on this computer.
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { readFile, rm, writeFile } from 'node:fs/promises';
import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';
import type { AddressInfo } from 'node:net';
import { join } from 'node:path';

const SLOTS = ['cape', 'hat', 'face', 'back', 'wings', 'shoes', 'pet'] as const;
type Slot = (typeof SLOTS)[number];
const FOLDERS: Record<Slot, string> = { cape: 'capes', hat: 'hats', face: 'face', back: 'back', wings: 'wings', shoes: 'shoes', pet: 'pets' };
const UUID = /^[0-9a-f]{32}$/;
const STATIC = /^\/(models|textures|thumbnails)\/([a-z0-9_]+\/)?[a-z0-9_]+\.(json|png)$/;
/** Preferred port; if it is taken, the system picks a free one. */
const PORT = 8778;

interface CatalogEntry {
  id: string;
  type: Slot;
  rarity: string;
  name: string;
  names: Record<string, string>;
  texture: string;
  model: string | null;
  thumbnail: string | null;
}

type PlayerRow = Partial<Record<Slot, string | null>> & { custom_cape?: string | null };

interface Data {
  players: Record<string, PlayerRow>;
}

export interface LocalCosmeticsServer {
  url: string;
  close(): void;
}

class HttpFailure extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

/** Same catalog shape as backend/tools/build-assets.mjs produces for the hosted server. */
function loadCatalog(assetsDir: string): CatalogEntry[] {
  const raw = JSON.parse(readFileSync(join(assetsDir, 'cosmetics.json'), 'utf8')) as {
    id: string;
    type: Slot;
    rarity?: string;
    names?: Record<string, string>;
  }[];
  return raw.map((c) => ({
    id: c.id,
    type: c.type,
    rarity: c.rarity ?? 'common',
    name: c.names?.en ?? c.id,
    names: c.names ?? {},
    texture: `/textures/${FOLDERS[c.type]}/${c.id}.png`,
    model: c.type === 'cape' ? null : `/models/${c.id}.json`,
    thumbnail: existsSync(join(assetsDir, 'thumbnails', `${c.id}.png`)) ? `/thumbnails/${c.id}.png` : null,
  }));
}

function pngSize(buf: Buffer): { width: number; height: number } | null {
  const signature = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
  if (buf.length < 24 || signature.some((b, i) => buf[i] !== b) || buf.toString('latin1', 12, 16) !== 'IHDR') return null;
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) };
}

function readBody(req: IncomingMessage, limit: number): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    let size = 0;
    req.on('data', (chunk: Buffer) => {
      size += chunk.length;
      if (size > limit) {
        reject(new HttpFailure(413, 'Too large'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

/**
 * Starts the built-in server.
 * @param assetsDir folder with cosmetics.json, models/, textures/, thumbnails/
 * @param dataDir   folder for "who wears what" and uploaded capes
 */
export async function startLocalCosmetics(assetsDir: string, dataDir: string): Promise<LocalCosmeticsServer> {
  const catalog = loadCatalog(assetsDir);
  const byId = new Map(catalog.map((c) => [c.id, c]));
  const capesDir = join(dataDir, 'capes');
  const dataFile = join(dataDir, 'players.json');
  mkdirSync(capesDir, { recursive: true });

  let data: Data = { players: {} };
  try {
    const parsed = JSON.parse(readFileSync(dataFile, 'utf8')) as Data;
    if (parsed && typeof parsed.players === 'object' && parsed.players) data = parsed;
  } catch {
    // first start
  }
  const save = () => writeFileSync(dataFile, JSON.stringify(data, null, 2));

  /** Only return cosmetics that (still) exist. */
  const clean = (row: PlayerRow | undefined) => {
    const result: Record<string, string | null> = {};
    for (const slot of SLOTS) {
      const id = row?.[slot];
      result[slot] = id && byId.get(id)?.type === slot ? id : null;
    }
    const custom = row?.custom_cape ? `custom_${row.custom_cape}` : null;
    if (custom && row?.cape === custom) result.cape = custom;
    result.customCape = custom;
    return result;
  };

  const removeUnused = async (hash: string) => {
    if (!Object.values(data.players).some((p) => p.custom_cape === hash)) await rm(join(capesDir, `${hash}.png`), { force: true });
  };

  const send = (res: ServerResponse, status: number, body: Buffer | string, type: string, cache = 'no-store') => {
    res.writeHead(status, {
      'Content-Type': type,
      'Cache-Control': cache,
      'X-Content-Type-Options': 'nosniff',
    });
    res.end(body);
  };
  const json = (res: ServerResponse, status: number, body: unknown) => send(res, status, JSON.stringify(body), 'application/json');

  const player = (req: IncomingMessage): string => {
    const uuid = String(req.headers['x-dev-uuid'] ?? '');
    if (!UUID.test(uuid)) throw new HttpFailure(401, 'Not signed in');
    return uuid;
  };

  async function handle(req: IncomingMessage, res: ServerResponse): Promise<void> {
    const url = new URL(req.url ?? '/', 'http://127.0.0.1');
    const { method } = req;
    const path = url.pathname;

    if (method === 'GET' && path === '/api/cosmetics') return json(res, 200, catalog);

    if (method === 'GET' && path === '/api/players') {
      const uuids = [...new Set((url.searchParams.get('uuids') ?? '').split(',').filter((u) => UUID.test(u)))].slice(0, 100);
      const result: Record<string, unknown> = {};
      for (const uuid of uuids) if (data.players[uuid]) result[uuid] = clean(data.players[uuid]);
      return json(res, 200, result);
    }

    if (method === 'PUT' && path === '/api/me') {
      const uuid = player(req);
      let body: Record<string, unknown>;
      try {
        body = JSON.parse((await readBody(req, 10_000)).toString('utf8') || '{}') as Record<string, unknown>;
      } catch (e) {
        throw e instanceof HttpFailure ? e : new HttpFailure(400, 'Invalid JSON');
      }
      const row = data.players[uuid];
      const current = clean(row);
      for (const slot of SLOTS) {
        if (!(slot in body)) continue;
        const id = body[slot];
        const own = slot === 'cape' && id !== null && id === current.customCape;
        if (id !== null && !own && (typeof id !== 'string' || byId.get(id)?.type !== slot)) {
          return json(res, 400, { error: `Unknown cosmetic for ${slot}: ${String(id)}` });
        }
        current[slot] = id as string | null;
      }
      const { customCape: _ignored, ...slots } = current;
      data.players[uuid] = { ...slots, custom_cape: row?.custom_cape ?? null };
      save();
      return json(res, 200, clean(data.players[uuid]));
    }

    // Own cape (PNG in cape format 2:1, 64×32 to 1024×512) – equipped right away
    if (method === 'PUT' && path === '/api/me/cape') {
      const uuid = player(req);
      const png = await readBody(req, 1_500_000);
      const size = pngSize(png);
      if (!size || size.width !== size.height * 2 || size.width < 64 || size.width > 1024 || size.width % 64 !== 0) {
        return json(res, 400, { error: 'Cape must be a 2:1 PNG (64×32 to 1024×512)' });
      }
      const hash = createHash('sha256').update(png).digest('hex').slice(0, 16);
      await writeFile(join(capesDir, `${hash}.png`), png);
      const row = data.players[uuid];
      const { customCape: _ignored, ...slots } = clean(row);
      data.players[uuid] = { ...slots, cape: `custom_${hash}`, custom_cape: hash };
      save();
      if (row?.custom_cape && row.custom_cape !== hash) await removeUnused(row.custom_cape);
      return json(res, 200, clean(data.players[uuid]));
    }

    if (method === 'DELETE' && path === '/api/me/cape') {
      const uuid = player(req);
      const row = data.players[uuid];
      const { customCape: _ignored, ...slots } = clean(row);
      if (slots.cape?.startsWith('custom_')) slots.cape = null;
      data.players[uuid] = { ...slots, custom_cape: null };
      save();
      if (row?.custom_cape) await removeUnused(row.custom_cape);
      return json(res, 200, clean(data.players[uuid]));
    }

    const custom = /^\/textures\/capes\/custom_([0-9a-f]{16})\.png$/.exec(path);
    if (method === 'GET' && custom) {
      const png = await readFile(join(capesDir, `${custom[1]}.png`)).catch(() => null);
      if (!png) return json(res, 404, { error: 'Not found' });
      return send(res, 200, png, 'image/png', 'public, max-age=604800, immutable');
    }

    if (method === 'GET' && STATIC.test(path)) {
      const file = await readFile(join(assetsDir, ...path.split('/').filter(Boolean))).catch(() => null);
      if (!file) return json(res, 404, { error: 'Not found' });
      return send(res, 200, file, path.endsWith('.png') ? 'image/png' : 'application/json', 'public, max-age=600');
    }

    if (method === 'GET' && path === '/') {
      return json(res, 200, { name: 'moonvibe cosmetics (built-in)', cosmetics: catalog.length, players: Object.keys(data.players).length });
    }
    return json(res, 404, { error: 'Not found' });
  }

  const server: Server = createServer((req, res) => {
    // Read-only files and lists may be loaded by the launcher window (3D preview needs this for textures).
    // Writes get no such header, so no website can change anything here.
    if (req.method === 'GET') res.setHeader('Access-Control-Allow-Origin', '*');
    handle(req, res).catch((e: unknown) => {
      const status = e instanceof HttpFailure ? e.status : 500;
      if (!res.headersSent) json(res, status, { error: e instanceof HttpFailure ? e.message : 'Server error' });
      else res.end();
    });
  });

  const listen = (port: number) =>
    new Promise<void>((resolve, reject) => {
      server.once('error', reject);
      server.listen(port, '127.0.0.1', () => {
        server.off('error', reject);
        resolve();
      });
    });
  await listen(PORT).catch(() => listen(0));
  server.unref();

  return {
    url: `http://127.0.0.1:${(server.address() as AddressInfo).port}`,
    close: () => server.close(),
  };
}
