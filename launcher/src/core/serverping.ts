// Fragt einen Minecraft-Server direkt nach seinem Status – genau wie die Serverliste im Spiel ("Server List Ping").
// https://minecraft.wiki/w/Java_Edition_protocol/Server_List_Ping
import { resolveSrv } from 'node:dns/promises';
import { connect } from 'node:net';

export interface PingResult {
  players: number;
  maxPlayers: number;
  motd: string;
  icon: string | null;
  version: string | null;
}

/** Protokoll 1.8 – darauf antworten praktisch alle Server, auch PvP-Netzwerke mit ViaVersion */
const PROTOCOL = 47;

function varInt(value: number): Buffer {
  const bytes: number[] = [];
  let v = value >>> 0;
  do {
    let b = v & 0x7f;
    v >>>= 7;
    if (v !== 0) b |= 0x80;
    bytes.push(b);
  } while (v !== 0);
  return Buffer.from(bytes);
}

/** Liest eine VarInt ab "offset" – null, wenn noch nicht genug Daten da sind. */
function readVarInt(buf: Buffer, offset: number): { value: number; size: number } | null {
  let value = 0;
  for (let i = 0; i < 5; i++) {
    if (offset + i >= buf.length) return null;
    const b = buf[offset + i];
    value |= (b & 0x7f) << (7 * i);
    if (!(b & 0x80)) return { value, size: i + 1 };
  }
  throw new Error('VarInt too long');
}

function packet(id: number, ...fields: Buffer[]): Buffer {
  const body = Buffer.concat([varInt(id), ...fields]);
  return Buffer.concat([varInt(body.length), body]);
}

function mcString(s: string): Buffer {
  const bytes = Buffer.from(s, 'utf8');
  return Buffer.concat([varInt(bytes.length), bytes]);
}

/** MOTD als reiner Text (Chat-Komponenten zusammensetzen, §-Farbcodes entfernen). */
type Chat = string | Chat[] | { text?: string; extra?: Chat[] };
function plainText(c: Chat | undefined): string {
  if (c === undefined || c === null) return '';
  if (typeof c === 'string') return c;
  if (Array.isArray(c)) return c.map(plainText).join('');
  return (c.text ?? '') + (c.extra ?? []).map(plainText).join('');
}

/** Echte Adresse: Server wie "minemen.club" verweisen per SRV-Eintrag auf einen anderen Host/Port. */
async function target(address: string): Promise<{ host: string; port: number }> {
  const [host, port] = address.split(':');
  if (port) return { host, port: Number(port) };
  try {
    const [srv] = await resolveSrv(`_minecraft._tcp.${host}`);
    if (srv) return { host: srv.name, port: srv.port };
  } catch {
    // kein SRV-Eintrag
  }
  return { host, port: 25565 };
}

export async function pingServer(address: string, timeoutMs = 5000): Promise<PingResult> {
  const { host, port } = await target(address);
  const json = await new Promise<string>((resolve, reject) => {
    const socket = connect({ host, port });
    let data = Buffer.alloc(0);
    const fail = (e: Error) => {
      socket.destroy();
      reject(e);
    };
    socket.setTimeout(timeoutMs, () => fail(new Error('timeout')));
    socket.on('error', fail);
    socket.on('connect', () => {
      const portBuf = Buffer.alloc(2);
      portBuf.writeUInt16BE(port);
      // Handshake (nächster Zustand 1 = Status), dann Statusanfrage
      socket.write(packet(0x00, varInt(PROTOCOL), mcString(host), portBuf, varInt(1)));
      socket.write(packet(0x00));
    });
    socket.on('data', (chunk) => {
      data = Buffer.concat([data, chunk]);
      try {
        const length = readVarInt(data, 0);
        if (!length || data.length < length.size + length.value) return;
        let offset = length.size;
        const id = readVarInt(data, offset)!;
        offset += id.size;
        if (id.value !== 0x00) return fail(new Error('unexpected packet'));
        const strLen = readVarInt(data, offset)!;
        offset += strLen.size;
        socket.destroy();
        resolve(data.subarray(offset, offset + strLen.value).toString('utf8'));
      } catch (e) {
        fail(e as Error);
      }
    });
    socket.on('close', () => reject(new Error('connection closed')));
  });
  const status = JSON.parse(json) as {
    description?: Chat;
    players?: { online?: number; max?: number };
    favicon?: string;
    version?: { name?: string };
  };
  return {
    players: status.players?.online ?? 0,
    maxPlayers: status.players?.max ?? 0,
    // Zeilen sind oft mit Leerzeichen zentriert – in der Liste wirkt das nur verrutscht
    motd: plainText(status.description)
      .replace(/§./g, '')
      .split('\n')
      .map((line) => line.trim())
      .join('\n')
      .trim(),
    icon: status.favicon?.startsWith('data:image/') ? status.favicon : null,
    version: status.version?.name?.replace(/§./g, '') ?? null,
  };
}
