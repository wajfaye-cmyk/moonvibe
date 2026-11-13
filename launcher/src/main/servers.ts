// Server-Liste mit Live-Status (Spielerzahl, Symbol, MOTD) – direkt beim Server abgefragt, notfalls über mcstatus.io.
import type { ServerInfo } from '../shared/types';
import { fetchJson } from '../core/http';
import { pingServer, type PingResult as ServerStatus } from '../core/serverping';
import { getSettings } from './store';

/** Empfohlene Server – große, bekannte Netzwerke mit PvP. */
const FEATURED: { address: string; name: string }[] = [
  { address: 'mc.hypixel.net', name: 'Hypixel' },
  { address: 'minemen.club', name: 'Minemen Club' },
  { address: 'play.pvplegacy.net', name: 'PvP Legacy' },
  { address: 'gommehd.net', name: 'GommeHD' },
  { address: 'play.cubecraft.net', name: 'CubeCraft' },
  { address: 'play.wynncraft.com', name: 'Wynncraft' },
  { address: 'hub.mc-complex.com', name: 'Complex Gaming' },
  { address: 'play.manacube.com', name: 'ManaCube' },
];

interface StatusResponse {
  online: boolean;
  players?: { online: number; max: number };
  motd?: { clean: string };
  icon?: string | null;
  version?: { name_clean?: string };
}

const cache = new Map<string, { at: number; info: ServerInfo }>();

/** Ersatzweg über mcstatus.io, falls die direkte Abfrage blockiert ist (z. B. Firewall im Schul- oder Firmennetz). */
async function mcstatus(address: string): Promise<ServerStatus | null> {
  const s = await fetchJson<StatusResponse>(`https://api.mcstatus.io/v2/status/java/${encodeURIComponent(address)}`);
  if (!s.online) return null;
  return {
    players: s.players?.online ?? 0,
    maxPlayers: s.players?.max ?? 0,
    motd: (s.motd?.clean ?? '').trim(),
    icon: s.icon ?? null,
    version: s.version?.name_clean ?? null,
  };
}

async function status(address: string, name: string, featured: boolean): Promise<ServerInfo> {
  const cached = cache.get(address);
  if (cached && Date.now() - cached.at < 60_000) return { ...cached.info, featured };
  // direkt fragen wie die Serverliste im Spiel – genauer als mcstatus.io, das Server manchmal fälschlich als offline meldet
  const s = await pingServer(address)
    .catch(() => mcstatus(address))
    .catch(() => null);
  const info: ServerInfo = s
    ? { address, name, featured, online: true, ...s }
    : { address, name, featured, online: false, players: 0, maxPlayers: 0, motd: '', icon: null, version: null };
  cache.set(address, { at: Date.now(), info });
  return info;
}

export async function serverList(): Promise<ServerInfo[]> {
  const custom = getSettings().customServers.map((address) => ({ address, name: address, featured: false }));
  const all = [...FEATURED.map((s) => ({ ...s, featured: true })), ...custom];
  return Promise.all(all.map((s) => status(s.address, s.name, s.featured)));
}
