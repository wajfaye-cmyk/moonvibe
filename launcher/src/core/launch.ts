// Baut die Startargumente zusammen und startet Minecraft.
import { spawn, type ChildProcess } from 'node:child_process';
import { mkdir } from 'node:fs/promises';
import { delimiter, join } from 'node:path';
import { LAUNCHER_VERSION } from './http';
import type { Installation } from './install';
import { rulesAllow } from './rules';
import type { Argument } from './versions';

export interface LaunchAccount {
  name: string;
  /** UUID ohne Bindestriche */
  uuid: string;
  accessToken: string;
  xuid: string;
  userType: 'msa' | 'legacy';
}

export interface LaunchOptions {
  install: Installation;
  gameDir: string;
  account: LaunchAccount;
  clientId: string;
  memoryMb: number;
  width?: number | null;
  height?: number | null;
  /** zusätzliche -D-Eigenschaften, z. B. die Adresse des Cosmetics-Servers für die Mod */
  systemProperties?: Record<string, string>;
  /** zusätzliche Spiel-Argumente, z. B. --quickPlaySingleplayer <Welt> */
  extraGameArgs?: string[];
  /** Direkt diesem Server beitreten (Adresse, optional mit :Port) */
  joinServer?: string | null;
}

/** Kann diese Version per Quick Play direkt einem Server beitreten (ab 1.20)? */
function supportsQuickPlay(args: Argument[] | undefined): boolean {
  return (args ?? []).some((a) => typeof a !== 'string' && a.rules?.some((r) => r.features?.is_quick_play_multiplayer));
}

function expand(args: Argument[] | undefined, features: Record<string, boolean>): string[] {
  const out: string[] = [];
  for (const arg of args ?? []) {
    if (typeof arg === 'string') out.push(arg);
    else if (rulesAllow(arg.rules, features)) out.push(...(Array.isArray(arg.value) ? arg.value : [arg.value]));
  }
  return out;
}

export function buildArguments(opts: LaunchOptions): string[] {
  const { install, account } = opts;
  const v = install.version;
  const customResolution = Boolean(opts.width && opts.height);
  const quickPlay = Boolean(opts.joinServer) && supportsQuickPlay(v.arguments?.game);
  const features = { has_custom_resolution: customResolution, is_quick_play_multiplayer: quickPlay };

  const vars: Record<string, string> = {
    auth_player_name: account.name,
    version_name: v.id,
    game_directory: opts.gameDir,
    assets_root: install.assetsDir,
    game_assets: install.assetsDir,
    assets_index_name: v.assetIndex?.id ?? v.assets ?? v.id,
    auth_uuid: account.uuid,
    auth_access_token: account.accessToken,
    auth_session: `token:${account.accessToken}:${account.uuid}`,
    auth_xuid: account.xuid,
    clientid: opts.clientId,
    user_type: account.userType,
    user_properties: '{}',
    // Wird unten links im Hauptmenü angezeigt: "Minecraft 26.3/moonvibe"
    version_type: 'moonvibe',
    natives_directory: install.nativesDir,
    launcher_name: 'moonvibe',
    launcher_version: LAUNCHER_VERSION,
    classpath: install.classpath.join(delimiter),
    classpath_separator: delimiter,
    library_directory: install.librariesDir,
    resolution_width: String(opts.width ?? ''),
    resolution_height: String(opts.height ?? ''),
    quickPlayMultiplayer: opts.joinServer ?? '',
  };
  const fill = (s: string) => s.replace(/\$\{(\w+)\}/g, (m, key: string) => vars[key] ?? m);

  const jvm = [
    `-Xmx${opts.memoryMb}M`,
    `-Xms${Math.min(opts.memoryMb, 1024)}M`,
    ...Object.entries(opts.systemProperties ?? {}).map(([k, val]) => `-D${k}=${val}`),
    ...expand(v.arguments?.jvm, features).map(fill),
  ];
  if (install.logConfigArg) jvm.push(install.logConfigArg);

  const game = [...(v.minecraftArguments?.split(' ') ?? []), ...expand(v.arguments?.game, features)].map(fill);
  // Alte Versionen kennen --width/--height nur über minecraftArguments nicht – dann selbst anhängen.
  if (customResolution && !game.includes('--width')) game.push('--width', vars.resolution_width, '--height', vars.resolution_height);

  // Ältere Versionen kennen kein Quick Play, aber --server/--port
  if (opts.joinServer && !quickPlay) {
    const [host, port = '25565'] = opts.joinServer.split(':');
    game.push('--server', host, '--port', port);
  }
  game.push(...(opts.extraGameArgs ?? []));
  return [...jvm, v.mainClass, ...game];
}

export async function launchGame(opts: LaunchOptions): Promise<ChildProcess> {
  // Manche älteren Mods (z. B. FerriteCore für 1.17) stürzen ab, wenn es den config-Ordner noch nicht gibt
  await mkdir(join(opts.gameDir, 'config'), { recursive: true });
  return spawn(opts.install.javaPath, buildArguments(opts), {
    cwd: opts.gameDir,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
}
