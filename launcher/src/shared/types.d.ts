// Typen, die Hauptprozess (Node) und Oberfläche (Renderer) gemeinsam nutzen.
import type { Lang } from './i18n';

export type Loader = 'fabric' | 'legacy-fabric';

export interface GameVersion {
  id: string;
  releaseTime: string;
  /** Mod-Loader, mit dem diese Version gestartet wird (null = Vanilla). */
  loader: Loader | null;
  /** true, wenn es für diese Version eine eigene moonvibe-Mod gibt (HUD, Zoom, Cosmetics …). */
  moonvibe: boolean;
}

/** hide = Launcher während des Spielens ausblenden, keep = offen lassen */
export type LauncherBehavior = 'hide' | 'keep';

export interface Settings {
  language: Lang;
  /** eigene Server (Adresse) zusätzlich zu den empfohlenen */
  customServers: string[];
  memoryMb: number;
  lastVersion: string | null;
  launcherBehavior: LauncherBehavior;
  /** Adresse des Cosmetics-Servers. */
  apiUrl: string;
  gameWidth: number | null;
  gameHeight: number | null;
}

export interface AccountInfo {
  id: string;
  type: 'microsoft' | 'offline';
  name: string;
  uuid: string;
}

export interface AccountsState {
  accounts: AccountInfo[];
  selectedId: string | null;
  /** Offline-Accounts sind nur im Entwicklermodus erlaubt. */
  offlineAllowed: boolean;
}

export interface Progress {
  /** Übersetzungsschlüssel, z. B. "stage.assets" */
  stage: string;
  done: number;
  total: number;
}

export type GameState =
  | { running: false; exitCode?: number | null; crashed?: boolean; lastLines?: string[] }
  | { running: true; version: string };

export type ProjectType = 'mod' | 'resourcepack' | 'shader';

export interface SearchHit {
  projectId: string;
  slug: string;
  title: string;
  description: string;
  author: string;
  downloads: number;
  iconUrl: string | null;
}

export type SearchSort = 'relevance' | 'downloads' | 'follows' | 'newest' | 'updated';

export interface SearchParams {
  query: string;
  type: ProjectType;
  gameVersion: string;
  offset: number;
  /** Modrinth-Kategorie (z. B. "optimization") oder null = alle */
  category?: string | null;
  /** Sortierung (Standard: Relevanz bei Suchtext, sonst Downloads) */
  sort?: SearchSort | null;
}

export interface SearchResult {
  hits: SearchHit[];
  total: number;
}

export interface InstalledItem {
  file: string;
  title: string;
  enabled: boolean;
  /** pack = automatisch von moonvibe, moonvibe = eigene Mod, user = selbst installiert */
  source: 'pack' | 'moonvibe' | 'user' | 'unknown';
  projectId: string | null;
  iconUrl: string | null;
}

export type CosmeticSlot = 'cape' | 'hat' | 'face' | 'back' | 'wings' | 'shoes' | 'pet';
export type Rarity = 'common' | 'rare' | 'epic' | 'legendary';

export interface Cosmetic {
  id: string;
  type: CosmeticSlot;
  rarity: Rarity;
  /** fertig gerendertes Vorschaubild vom Server (oder null) */
  thumbnailUrl: string | null;
  /** Name je Sprache (en, de, …) */
  names: Partial<Record<Lang, string>>;
  textureUrl: string;
  /** Hut/Flügel: Adresse des 3D-Modells (JSON); Capes haben keins */
  modelUrl: string | null;
  /** selbst hochgeladenes Cape des Spielers */
  custom?: boolean;
}

export type Equipment = Record<CosmeticSlot, string | null>;

export interface CosmeticsState {
  online: boolean;
  catalog: Cosmetic[];
  equipped: Equipment;
  /** ID des eigenen hochgeladenen Capes (steht dann auch im Katalog) */
  customCape: string | null;
  /** Grund (schon übersetzt), warum man gerade nichts auswählen kann. */
  error: string | null;
}

export interface SkinInfo {
  /** PNG als data:-URL (oder null → Standard-Skin) */
  dataUrl: string | null;
  slim: boolean;
}

export interface ServerInfo {
  address: string;
  name: string;
  featured: boolean;
  online: boolean;
  players: number;
  maxPlayers: number;
  motd: string;
  /** Server-Symbol als data:-URL */
  icon: string | null;
  version: string | null;
}

export interface NewsItem {
  id: string;
  date: string;
  tag: string;
  image: string;
  title: Partial<Record<Lang, string>>;
  text: Partial<Record<Lang, string>>;
}

export interface AppInfo {
  version: string;
  dataDir: string;
  dev: boolean;
  /** Cosmetics-Server, wenn in den Einstellungen nichts eingetragen ist ('' = keiner) */
  defaultApiUrl: string;
}

export interface MoonApi {
  appInfo(): Promise<AppInfo>;
  getVersions(): Promise<GameVersion[]>;
  getSettings(): Promise<Settings>;
  saveSettings(patch: Partial<Settings>): Promise<Settings>;
  systemMemoryMb(): Promise<number>;

  accounts(): Promise<AccountsState>;
  loginMicrosoft(): Promise<AccountsState>;
  addOffline(name: string): Promise<AccountsState>;
  selectAccount(id: string): Promise<AccountsState>;
  removeAccount(id: string): Promise<AccountsState>;
  skin(): Promise<SkinInfo>;

  play(version: string, server?: string): Promise<void>;
  servers(): Promise<ServerInfo[]>;
  news(): Promise<NewsItem[]>;
  /** Minecraft-Panorama (4 Seitenbilder) als data:-URLs für den Hintergrund */
  panorama(): Promise<string[]>;
  gameState(): Promise<GameState>;
  killGame(): Promise<void>;

  search(params: SearchParams): Promise<SearchResult>;
  installed(version: string, type: ProjectType): Promise<InstalledItem[]>;
  install(version: string, projectId: string, type: ProjectType): Promise<void>;
  removeInstalled(version: string, type: ProjectType, file: string): Promise<void>;
  toggleInstalled(version: string, type: ProjectType, file: string): Promise<void>;
  /** Gibt es Iris (und damit Shader) für diese Version? */
  shadersSupported(version: string): Promise<boolean>;

  cosmetics(): Promise<CosmeticsState>;
  cosmeticModel(id: string): Promise<unknown>;
  equip(slot: CosmeticSlot, id: string | null): Promise<CosmeticsState>;
  /** Bild auswählen (Dateidialog) → data:-URL oder null bei Abbruch */
  pickImage(): Promise<string | null>;
  /** fertiges Cape-PNG hochladen und anziehen */
  uploadCape(png: Uint8Array): Promise<CosmeticsState>;
  removeCustomCape(): Promise<CosmeticsState>;

  openFolder(kind: 'data' | 'instance' | 'logs', version?: string): Promise<void>;
  windowControl(action: 'minimize' | 'close'): void;

  onProgress(cb: (p: Progress | null) => void): void;
  onGameState(cb: (s: GameState) => void): void;
  onGameLog(cb: (line: string) => void): void;
}
