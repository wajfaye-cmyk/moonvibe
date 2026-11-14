// moonvibe Cosmetics-Server (Cloudflare Worker)
// Speichert, welcher Spieler welche Cosmetics trägt, nimmt eigene Capes an und liefert Katalog und News aus.
// Modelle, Texturen und Vorschaubilder liegen als Dateien in public/ – die liefert Cloudflare direkt aus.
//
// Lokal testen:   npm run dev      (http://127.0.0.1:8777, Testmodus: erlaubt Offline-Accounts)
// Online stellen: npm run deploy   (README → "Cosmetics-Server online stellen")
// Neue Cosmetics: node tools/generate-cosmetics.mjs  (Vorschaubilder: im Launcher "npm run thumbnails")
import CATALOG from '../.build/catalog.json';
import NEWS from '../news.json';

const SLOTS = ['cape', 'hat', 'face', 'back', 'wings', 'shoes', 'pet'];
const BY_ID = new Map(CATALOG.map((c) => [c.id, c]));
const SESSION_DAYS = 30;
const UUID = /^[0-9a-f]{32}$/;

// ---------- Hilfen ----------
const HEADERS = { 'Access-Control-Allow-Origin': '*', 'X-Content-Type-Options': 'nosniff' };

function json(status, body, cache = 'no-store') {
  return new Response(JSON.stringify(body), { status, headers: { ...HEADERS, 'Content-Type': 'application/json', 'Cache-Control': cache } });
}

const httpError = (status, message) => Object.assign(new Error(message), { status });

const hex = (bytes) => [...new Uint8Array(bytes)].map((b) => b.toString(16).padStart(2, '0')).join('');
const randomHex = (length) => hex(crypto.getRandomValues(new Uint8Array(length)));
const sha256 = async (data) => hex(await crypto.subtle.digest('SHA-256', typeof data === 'string' ? new TextEncoder().encode(data) : data));

async function readRaw(request, limit) {
  if (Number(request.headers.get('Content-Length') ?? 0) > limit) throw httpError(413, 'Too large');
  const chunks = [];
  let size = 0;
  const reader = request.body?.getReader();
  for (let part = await reader?.read(); part && !part.done; part = await reader.read()) {
    size += part.value.length;
    if (size > limit) throw httpError(413, 'Too large');
    chunks.push(part.value);
  }
  const body = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    body.set(chunk, offset);
    offset += chunk.length;
  }
  return body;
}

async function readBody(request) {
  try {
    return JSON.parse(new TextDecoder().decode(await readRaw(request, 10_000)) || '{}');
  } catch (e) {
    throw httpError(e.status ?? 400, e.status ? e.message : 'Invalid JSON');
  }
}

/** Breite/Höhe aus dem PNG-Kopf lesen (null = kein PNG). */
function pngSize(buf) {
  const signature = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
  if (buf.length < 24 || signature.some((b, i) => buf[i] !== b) || new TextDecoder().decode(buf.subarray(12, 16)) !== 'IHDR') return null;
  const view = new DataView(buf.buffer, buf.byteOffset);
  return { width: view.getUint32(16), height: view.getUint32(20) };
}

/** Bremse gegen Spam: true = zu viele Anfragen von dieser Adresse. */
async function limited(limiter, key) {
  if (!limiter) return false;
  return !(await limiter.limit({ key })).success;
}

// ---------- Spieler ----------
/** Nur Cosmetics zurückgeben, die es (noch) gibt. row = Zeile aus "players" (oder null). */
function cleanEquipment(row) {
  const result = {};
  for (const slot of SLOTS) {
    const id = row?.[slot];
    result[slot] = id && BY_ID.get(id)?.type === slot ? id : null;
  }
  const custom = row?.custom_cape ? `custom_${row.custom_cape}` : null;
  if (custom && row.cape === custom) result.cape = custom;
  result.customCape = custom;
  return result;
}

const loadPlayer = (env, uuid) => env.DB.prepare('SELECT * FROM players WHERE uuid = ?').bind(uuid).first();

/** Speichert die Auswahl (Plätze aus equipment) und das eigene Cape; gibt die neue Zeile zurück. */
async function savePlayer(env, uuid, equipment, customCape) {
  const row = { uuid, ...Object.fromEntries(SLOTS.map((slot) => [slot, equipment[slot] ?? null])), custom_cape: customCape ?? null };
  const columns = Object.keys(row);
  await env.DB.prepare(`INSERT OR REPLACE INTO players (${columns.join(', ')}) VALUES (${columns.map(() => '?').join(', ')})`)
    .bind(...Object.values(row))
    .run();
  return row;
}

/** Altes eigenes Cape löschen, wenn es niemand mehr nutzt. */
async function removeUnused(env, hash) {
  const used = await env.DB.prepare('SELECT 1 FROM players WHERE custom_cape = ? LIMIT 1').bind(hash).first();
  if (!used) await env.CAPES.delete(hash);
}

// ---------- Anmeldung ----------
// Der Minecraft-Token verlässt nie den PC des Spielers. Wie ein Minecraft-Server prüfen wir über Mojangs
// Session-Server, wem der Account gehört: Der Launcher meldet sich mit einer Zufallskennung bei Mojang an ("join"),
// wir fragen Mojang, wer das war ("hasJoined"), und geben dafür eine eigene moonvibe-Sitzung aus.

/** Zufallskennung für Mojangs "join" (gilt 2 Minuten, nur einmal nutzbar). */
async function createChallenge(env) {
  const serverId = randomHex(20);
  await env.DB.prepare('INSERT INTO challenges (id, until) VALUES (?, ?)').bind(serverId, Date.now() + 2 * 60 * 1000).run();
  return serverId;
}

/** Hat sich dieser Spieler wirklich mit der Kennung bei Mojang angemeldet? Dann neue Sitzung. */
async function createSession(env, name, serverId) {
  if (!/^[0-9a-f]{40}$/.test(serverId) || !/^[A-Za-z0-9_]{1,16}$/.test(name)) return null;
  const challenge = await env.DB.prepare('DELETE FROM challenges WHERE id = ? RETURNING until').bind(serverId).first();
  if (!challenge || challenge.until < Date.now()) return null;
  const res = await fetch(
    `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${encodeURIComponent(name)}&serverId=${serverId}`,
  );
  if (res.status !== 200) return null;
  const profile = await res.json();
  const token = randomHex(32);
  const expiresAt = Date.now() + SESSION_DAYS * 24 * 60 * 60 * 1000;
  await env.DB.prepare('INSERT INTO sessions (hash, uuid, until) VALUES (?, ?, ?)').bind(await sha256(token), profile.id, expiresAt).run();
  return { token, uuid: profile.id, expiresAt };
}

// ---------- Username accounts (no Microsoft login) ----------
// Players who only enter a name (like in TLauncher) cannot be confirmed by Mojang. Instead, the first launcher
// that uses a name reserves it with a secret of its installation; later sign-ins with that name need the same
// secret. Their UUID is the one Minecraft itself gives offline players, so it never collides with real accounts.
// Switch this off with the variable MOONVIBE_OFFLINE = "0" (wrangler.jsonc → vars).

/** MD5 (only needed for Minecraft's offline UUIDs; written out here so it runs everywhere). */
function md5(text) {
  const input = new TextEncoder().encode(text);
  const length = (((input.length + 8) >> 6) + 1) << 6;
  const data = new Uint8Array(length);
  data.set(input);
  data[input.length] = 0x80;
  const view = new DataView(data.buffer);
  view.setUint32(length - 8, input.length << 3, true);
  view.setUint32(length - 4, input.length >>> 29, true);
  const S = [7, 12, 17, 22, 5, 9, 14, 20, 4, 11, 16, 23, 6, 10, 15, 21];
  const K = Array.from({ length: 64 }, (_, i) => Math.floor(Math.abs(Math.sin(i + 1)) * 2 ** 32) >>> 0);
  let [a0, b0, c0, d0] = [0x67452301, 0xefcdab89, 0x98badcfe, 0x10325476];
  for (let offset = 0; offset < length; offset += 64) {
    let [a, b, c, d] = [a0, b0, c0, d0];
    for (let i = 0; i < 64; i++) {
      const round = i >> 4;
      const f = round === 0 ? (b & c) | (~b & d) : round === 1 ? (d & b) | (~d & c) : round === 2 ? b ^ c ^ d : c ^ (b | ~d);
      const g = round === 0 ? i : round === 1 ? (5 * i + 1) % 16 : round === 2 ? (3 * i + 5) % 16 : (7 * i) % 16;
      const sum = (a + f + K[i] + view.getUint32(offset + g * 4, true)) >>> 0;
      const shift = S[round * 4 + (i % 4)];
      [a, d, c, b] = [d, c, b, (b + ((sum << shift) | (sum >>> (32 - shift)))) >>> 0];
    }
    [a0, b0, c0, d0] = [(a0 + a) >>> 0, (b0 + b) >>> 0, (c0 + c) >>> 0, (d0 + d) >>> 0];
  }
  const out = new Uint8Array(16);
  [a0, b0, c0, d0].forEach((word, i) => new DataView(out.buffer).setUint32(i * 4, word, true));
  return out;
}

/** UUID of an offline player, exactly like Minecraft computes it (without dashes). */
function offlineUuid(name) {
  const hash = md5(`OfflinePlayer:${name}`);
  hash[6] = (hash[6] & 0x0f) | 0x30;
  hash[8] = (hash[8] & 0x3f) | 0x80;
  return hex(hash);
}

/** Session for a username account: { token, uuid, expiresAt }, or 'taken' if another installation owns the name. */
async function createOfflineSession(env, name, key) {
  if (!/^[A-Za-z0-9_]{3,16}$/.test(name) || !/^[0-9a-f]{64}$/.test(key)) return null;
  const uuid = offlineUuid(name);
  const keyHash = await sha256(`${uuid}:${key}`);
  await env.DB.prepare('INSERT OR IGNORE INTO offline_accounts (uuid, key_hash, created) VALUES (?, ?, ?)').bind(uuid, keyHash, Date.now()).run();
  const owner = await env.DB.prepare('SELECT key_hash FROM offline_accounts WHERE uuid = ?').bind(uuid).first();
  if (owner?.key_hash !== keyHash) return 'taken';
  const token = randomHex(32);
  const expiresAt = Date.now() + SESSION_DAYS * 24 * 60 * 60 * 1000;
  await env.DB.prepare('INSERT INTO sessions (hash, uuid, until) VALUES (?, ?, ?)').bind(await sha256(token), uuid, expiresAt).run();
  return { token, uuid, expiresAt };
}

async function authenticate(request, env) {
  const auth = request.headers.get('Authorization') ?? '';
  if (auth.startsWith('Bearer ')) {
    const session = await env.DB.prepare('SELECT uuid FROM sessions WHERE hash = ? AND until > ?')
      .bind(await sha256(auth.slice(7)), Date.now())
      .first();
    if (session) return session.uuid;
  }
  // Nur im Testmodus: Offline-Accounts identifizieren sich über ihre UUID
  const devUuid = request.headers.get('X-Dev-Uuid') ?? '';
  if (env.MOONVIBE_DEV === '1' && UUID.test(devUuid)) return devUuid;
  return null;
}

// ---------- Anfragen ----------
async function handle(request, env) {
  const url = new URL(request.url);
  const { method } = request;
  const ip = request.headers.get('CF-Connecting-IP') ?? '?';

  if (method === 'GET' && url.pathname === '/api/cosmetics') return json(200, CATALOG);

  if (method === 'GET' && url.pathname === '/api/news') return json(200, NEWS, 'public, max-age=300');

  if (method === 'GET' && url.pathname === '/api/players') {
    // höchstens 100 auf einmal (mehr Platzhalter erlaubt die Datenbank nicht)
    const uuids = [...new Set((url.searchParams.get('uuids') ?? '').split(',').filter((u) => UUID.test(u)))].slice(0, 100);
    const result = {};
    if (uuids.length) {
      const { results } = await env.DB.prepare(`SELECT * FROM players WHERE uuid IN (${uuids.map(() => '?').join(', ')})`)
        .bind(...uuids)
        .all();
      for (const row of results) result[row.uuid] = cleanEquipment(row);
    }
    return json(200, result);
  }

  // Anmeldung, Schritt 1: Zufallskennung abholen (danach meldet sich der Launcher damit bei Mojang an)
  if (method === 'POST' && url.pathname === '/api/auth/challenge') {
    if (await limited(env.LIMIT_AUTH, ip)) return json(429, { error: 'Too many requests' });
    return json(200, { serverId: await createChallenge(env) });
  }

  // Anmeldung, Schritt 2: Mojang bestätigt den Spieler → moonvibe-Sitzung
  if (method === 'POST' && url.pathname === '/api/auth/session') {
    if (await limited(env.LIMIT_AUTH, ip)) return json(429, { error: 'Too many requests' });
    const { name, serverId } = await readBody(request);
    const session = await createSession(env, String(name ?? ''), String(serverId ?? ''));
    if (!session) return json(401, { error: 'Mojang could not confirm this account' });
    return json(200, session);
  }

  // Sign-in for username accounts (see above)
  if (method === 'POST' && url.pathname === '/api/auth/offline') {
    if (env.MOONVIBE_OFFLINE === '0') return json(403, { error: 'Username accounts are switched off on this server' });
    if (await limited(env.LIMIT_AUTH, ip)) return json(429, { error: 'Too many requests' });
    const { name, key } = await readBody(request);
    const session = await createOfflineSession(env, String(name ?? ''), String(key ?? ''));
    if (!session) return json(400, { error: 'Invalid name' });
    if (session === 'taken') return json(409, { error: 'This username is already used by another player' });
    return json(200, session);
  }

  if (method === 'PUT' && url.pathname === '/api/me') {
    if (await limited(env.LIMIT, ip)) return json(429, { error: 'Too many requests' });
    const uuid = await authenticate(request, env);
    if (!uuid) return json(401, { error: 'Not signed in' });
    const body = await readBody(request);
    const row = await loadPlayer(env, uuid);
    const current = cleanEquipment(row);
    for (const slot of SLOTS) {
      if (!(slot in body)) continue;
      const id = body[slot];
      const own = slot === 'cape' && id !== null && id === current.customCape;
      if (id !== null && !own && BY_ID.get(id)?.type !== slot) return json(400, { error: `Unknown cosmetic for ${slot}: ${id}` });
      current[slot] = id;
    }
    return json(200, cleanEquipment(await savePlayer(env, uuid, current, row?.custom_cape)));
  }

  // Eigenes Cape hochladen (PNG im Cape-Format 2:1, 64×32 bis 1024×512) – wird direkt angezogen
  if (method === 'PUT' && url.pathname === '/api/me/cape') {
    if (await limited(env.LIMIT_CAPE, ip)) return json(429, { error: 'Too many requests' });
    const uuid = await authenticate(request, env);
    if (!uuid) return json(401, { error: 'Not signed in' });
    const png = await readRaw(request, 1_500_000);
    const size = pngSize(png);
    if (!size || size.width !== size.height * 2 || size.width < 64 || size.width > 1024 || size.width % 64 !== 0) {
      return json(400, { error: 'Cape must be a 2:1 PNG (64×32 to 1024×512)' });
    }
    const hash = (await sha256(png)).slice(0, 16);
    await env.CAPES.put(hash, png);
    const row = await loadPlayer(env, uuid);
    const saved = await savePlayer(env, uuid, { ...cleanEquipment(row), cape: `custom_${hash}` }, hash);
    if (row?.custom_cape && row.custom_cape !== hash) await removeUnused(env, row.custom_cape);
    return json(200, cleanEquipment(saved));
  }

  if (method === 'DELETE' && url.pathname === '/api/me/cape') {
    if (await limited(env.LIMIT, ip)) return json(429, { error: 'Too many requests' });
    const uuid = await authenticate(request, env);
    if (!uuid) return json(401, { error: 'Not signed in' });
    const row = await loadPlayer(env, uuid);
    const current = cleanEquipment(row);
    if (current.cape?.startsWith('custom_')) current.cape = null;
    const saved = await savePlayer(env, uuid, current, null);
    if (row?.custom_cape) await removeUnused(env, row.custom_cape);
    return json(200, cleanEquipment(saved));
  }

  const custom = url.pathname.match(/^\/textures\/capes\/custom_([0-9a-f]{16})\.png$/);
  if (method === 'GET' && custom) {
    const png = await env.CAPES.get(custom[1], 'arrayBuffer');
    if (!png) return json(404, { error: 'Not found' });
    // Schlüssel = Prüfsumme, das Bild ändert sich also nie
    return new Response(png, { headers: { ...HEADERS, 'Content-Type': 'image/png', 'Cache-Control': 'public, max-age=604800, immutable' } });
  }

  if (method === 'GET' && url.pathname === '/') {
    const { players } = await env.DB.prepare('SELECT COUNT(*) AS players FROM players').first();
    return json(200, { name: 'moonvibe cosmetics', cosmetics: CATALOG.length, players });
  }
  return json(404, { error: 'Not found' });
}

export default {
  async fetch(request, env) {
    try {
      return await handle(request, env);
    } catch (e) {
      if (!e.status) console.error(e);
      return json(e.status ?? 500, { error: e.status ? e.message : 'Server error' });
    }
  },

  // Einmal am Tag: abgelaufene Sitzungen und Zufallskennungen wegräumen
  async scheduled(_event, env) {
    const now = Date.now();
    await env.DB.batch([
      env.DB.prepare('DELETE FROM sessions WHERE until < ?').bind(now),
      env.DB.prepare('DELETE FROM challenges WHERE until < ?').bind(now),
    ]);
  },
};
