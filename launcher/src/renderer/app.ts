// Oberfläche des Launchers. Spricht über window.mv (siehe preload.ts) mit dem Hauptprozess.
import { LANGUAGES, isLang, translate, type I18nKey, type I18nParams, type Lang } from '../shared/i18n';
import type {
  AccountsState,
  Cosmetic,
  CosmeticSlot,
  CosmeticsState,
  MoonApi,
  GameState,
  GameVersion,
  InstalledItem,
  NewsItem,
  ProjectType,
  SearchHit,
  SearchSort,
  ServerInfo,
  Settings,
} from '../shared/types';
import { averageColor, buildCapeTexture, canUseAsTemplate, canvasToPng, hasTransparency, looksLikeTemplate, type CapeOptions } from './capeart.js';
import { Dropdown, type DropdownOption } from './dropdown.js';
import { LogParser } from './log.js';
import type { ModelJson } from './mcmodel.js';
import { BACK_SLOTS, PlayerPreview, renderThumbnail, SLOTS } from './preview3d.js';

declare global {
  interface Window {
    mv: MoonApi;
  }
}

const mv = window.mv;

// ---------- Hilfsfunktionen ----------
function $<T extends HTMLElement = HTMLElement>(id: string): T {
  const el = document.getElementById(id);
  if (!el) throw new Error(`#${id} fehlt`);
  return el as T;
}

function h<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props: Partial<HTMLElementTagNameMap[K]> & { className?: string } = {},
  ...children: (Node | string | null)[]
): HTMLElementTagNameMap[K] {
  const el = Object.assign(document.createElement(tag), props);
  for (const child of children) if (child !== null) el.append(child);
  return el;
}

/** SVG-Symbol aus Pfaden (24×24-Raster, mehrere Pfade mit | getrennt). */
function icon(paths: string): SVGSVGElement {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  for (const d of paths.split('|')) {
    const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    p.setAttribute('d', d);
    svg.append(p);
  }
  return svg;
}
const ICON = {
  power: 'M12 3v8|M6.3 7.3a8 8 0 1 0 11.4 0',
  trash: 'M4 7h16|M9 7V4h6v3|M6 7l1 13h10l1-13',
  globe: 'M3 12h18|M12 3a14 14 0 0 1 0 18|M12 3a14 14 0 0 0 0 18|M12 3a9 9 0 1 0 0 18a9 9 0 0 0 0-18',
  cube: 'M12 3l8 4.5v9L12 21l-8-4.5v-9z|M12 12l8-4.5|M12 12v9|M12 12L4 7.5',
  cape: 'M8 3h8l2 4-2 14H8L6 7z|M8 3l4 3 4-3',
  hat: 'M4 17h16|M7 17V9a5 5 0 0 1 10 0v8|M3 20h18',
  face: 'M3 10h18|M3 10a4 4 0 0 0 8 0|M13 10a4 4 0 0 0 8 0|M11 11h2',
  back: 'M7 7h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z|M9 7V5a3 3 0 0 1 6 0v2|M8 13h8',
  wings: 'M12 9c-2-4-7-6-10-5 1 6 4 10 10 11|M12 9c2-4 7-6 10-5-1 6-4 10-10 11|M12 9v10',
  shoes: 'M3 16v-5l4-1 3 3 5 1 6 2v3H3z|M3 19h18|M8 12l1 1|M11 14l1 1',
  pet: 'M6 10a2 2 0 1 0 0-4a2 2 0 0 0 0 4|M18 10a2 2 0 1 0 0-4a2 2 0 0 0 0 4|M9 6a2 2 0 1 0 0-4a2 2 0 0 0 0 4|M15 6a2 2 0 1 0 0-4a2 2 0 0 0 0 4|M12 11c-4 0-6 4-6 7 0 2 2 3 6 3s6-1 6-3c0-3-2-7-6-7z',
  plus: 'M12 5v14|M5 12h14',
  edit: 'M4 20h4L19 9l-4-4L4 16z|M13 7l4 4',
};

let lang: Lang = 'en';
const t = (key: I18nKey, params?: I18nParams) => translate(lang, key, params);

function errorText(e: unknown): string {
  const msg = e instanceof Error ? e.message : String(e);
  return msg.replace(/^Error invoking remote method '[^']+': (Error: )?/, '');
}

function showError(el: HTMLElement, e: unknown | null): void {
  el.hidden = e === null;
  el.textContent = e === null ? '' : errorText(e);
}

let toastTimer = 0;
function toast(text: string): void {
  const el = $('toast');
  el.textContent = text;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => (el.hidden = true), 2800);
}

const avatarUrl = (uuid: string) => `https://mc-heads.net/avatar/${uuid}/64`;
const formatNumber = (n: number) => new Intl.NumberFormat(lang, { notation: 'compact', maximumFractionDigits: 1 }).format(n);

// ---------- Zustand ----------
let versions: GameVersion[] = [];
let settings: Settings;
let accounts: AccountsState;
let game: GameState = { running: false };
let starting = false;
let servers: ServerInfo[] = [];
let newsItems: NewsItem[] = [];

// ---------- Hintergrund: bewegtes Panorama ----------
async function loadPanorama(): Promise<void> {
  try {
    const sources = await mv.panorama();
    const images = await Promise.all(
      sources.map(async (src) => {
        const img = new Image();
        img.src = src;
        await img.decode();
        return img;
      }),
    );
    const size = 720;
    const canvas = h('canvas', { width: size * images.length, height: size });
    const ctx = canvas.getContext('2d')!;
    images.forEach((img, i) => ctx.drawImage(img, i * size, 0, size, size));
    const el = $('panorama');
    el.style.backgroundImage = `url(${canvas.toDataURL('image/jpeg', 0.85)})`;
    el.classList.add('ready');
    let offset = 0;
    let last = performance.now();
    const step = (now: number) => {
      const strip = el.clientHeight * images.length;
      offset = (offset + (now - last) * 0.012) % strip;
      last = now;
      el.style.backgroundPositionX = `${-offset}px`;
      requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  } catch {
    // ohne Panorama bleibt der Farbverlauf-Hintergrund
  }
}

// ---------- Navigation ----------
let currentPage = 'home';
function moveIndicator(): void {
  const active = document.querySelector<HTMLElement>('.nav-item.active');
  const indicator = $('nav-indicator');
  if (!active) return;
  indicator.style.left = `${active.offsetLeft}px`;
  indicator.style.width = `${active.offsetWidth}px`;
}

function showPage(page: string): void {
  currentPage = page;
  document.querySelectorAll('.nav-item').forEach((b) => b.classList.toggle('active', (b as HTMLElement).dataset.page === page));
  document.querySelectorAll('.page').forEach((p) => p.classList.toggle('active', p.id === `page-${page}`));
  $('main').scrollTop = 0;
  moveIndicator();
  if (page === 'mods') void refreshMods();
  if (page === 'cosmetics') void openCosmetics();
  if (page === 'servers') void loadServers();
}

document.querySelectorAll<HTMLElement>('.nav-item').forEach((b) => b.addEventListener('click', () => showPage(b.dataset.page!)));
document.querySelectorAll<HTMLElement>('[data-goto]').forEach((b) => b.addEventListener('click', () => showPage(b.dataset.goto!)));
document.querySelectorAll<HTMLElement>('[data-window]').forEach((b) =>
  b.addEventListener('click', () => mv.windowControl(b.dataset.window as 'minimize' | 'close')),
);
window.addEventListener('resize', moveIndicator);

// ---------- Sprache ----------
const languagePicker = new Dropdown($('language-picker'), { icon: icon(ICON.globe).outerHTML, className: 'lang-dropdown', align: 'right' });
const settingsLanguage = new Dropdown($('settings-language'), { align: 'right' });

async function setLanguage(value: string): Promise<void> {
  if (!isLang(value) || value === lang) return;
  settings = await mv.saveSettings({ language: value });
  lang = settings.language;
  applyI18n();
  if (currentPage === 'mods') void runSearch();
}
languagePicker.change(setLanguage);
settingsLanguage.change(setLanguage);

function applyI18n(): void {
  document.documentElement.lang = lang;
  document.querySelectorAll<HTMLElement>('[data-i18n]').forEach((el) => (el.textContent = t(el.dataset.i18n as I18nKey)));
  document.querySelectorAll<HTMLInputElement>('[data-i18n-placeholder]').forEach((el) => (el.placeholder = t(el.dataset.i18nPlaceholder as I18nKey)));
  document.querySelectorAll<HTMLElement>('[data-i18n-title]').forEach((el) => (el.title = t(el.dataset.i18nTitle as I18nKey)));
  const langOptions = LANGUAGES.map((l) => ({ value: l.id, label: l.id.toUpperCase(), hint: l.name }));
  languagePicker.setOptions(langOptions, lang);
  settingsLanguage.setOptions(LANGUAGES.map((l) => ({ value: l.id, label: l.name })), lang);
  behaviorPicker.setOptions(
    [
      { value: 'hide', label: t('settings.behaviorHide') },
      { value: 'keep', label: t('settings.behaviorKeep') },
    ],
    settings?.launcherBehavior,
  );
  renderVersionPickers();
  renderPlayButton();
  renderAccounts();
  renderInstalled();
  renderModFilters();
  renderShaderHint();
  renderNews();
  renderServers();
  renderCosmetics();
  requestAnimationFrame(moveIndicator);
}

// ---------- Versionen ----------
const versionPicker = new Dropdown($('version-picker'), { className: 'hero-version', align: 'right' });
const modsVersionPicker = new Dropdown($('mods-version-picker'), { align: 'right' });

/** Nur Versionen, für die es die moonvibe-Mod gibt – dort funktioniert alles (HUD, Menü, Cosmetics …). */
function supportedVersions(): GameVersion[] {
  return versions.filter((v) => v.moonvibe);
}

function versionOption(v: GameVersion): DropdownOption {
  const newest = v.id === supportedVersions()[0]?.id;
  return {
    value: v.id,
    label: v.id,
    badge: newest ? '★' : undefined,
    // 1.8.9 ist die klassische PvP-Version, die übrigen alten laufen über Legacy Fabric
    hint: v.id === '1.8.9' ? 'PvP' : v.loader === 'legacy-fabric' ? 'Legacy' : 'Fabric',
  };
}

function renderVersionPickers(): void {
  if (!versions.length) return;
  const list = supportedVersions();
  if (!list.length) return;
  const current = versionPicker.value || settings.lastVersion || list[0].id;
  versionPicker.setOptions(list.map(versionOption), current);
  renderPlayButton();
  modsVersionPicker.setOptions(list.map(versionOption), modsVersionPicker.value || versionPicker.value);
}

versionPicker.change((value) => {
  renderPlayButton();
  void mv.saveSettings({ lastVersion: value }).then((s) => (settings = s));
});

// ---------- Spielen ----------
function renderPlayButton(): void {
  const button = $<HTMLButtonElement>('play-button');
  const version = versionPicker.value;
  button.disabled = starting || game.running;
  button.classList.toggle('running', game.running);
  $('play-label').textContent = game.running
    ? t('home.playing')
    : starting
      ? t('home.launching')
      : `${t('home.launch')}${version ? ` ${version}` : ''}`;
  $('play-sub').textContent = game.running ? version : starting ? '' : t('home.readyToLaunch');
  if (!starting) $('play-fill').style.width = '0';
}

async function launch(server?: string): Promise<void> {
  const version = versionPicker.value;
  if (!version) return;
  if (!accounts.selectedId) {
    openAccountModal();
    return;
  }
  if (server) showPage('home');
  starting = true;
  renderPlayButton();
  showError($('play-error'), null);
  $('crash-box').hidden = true;
  try {
    await mv.play(version, server);
  } catch (e) {
    showError($('play-error'), e);
  } finally {
    starting = false;
    renderPlayButton();
  }
}

$('play-button').addEventListener('click', () => void launch());

mv.onProgress((p) => {
  $('progress').hidden = p === null;
  if (!p) return;
  $('progress-stage').textContent = t(p.stage as I18nKey);
  $('progress-count').textContent = p.total > 1 ? `${p.done} / ${p.total}` : '';
  $('play-fill').style.width = `${p.total ? Math.round((p.done / p.total) * 100) : 0}%`;
});

mv.onGameState((s) => {
  game = s;
  renderPlayButton();
  if (!s.running && s.crashed) {
    const parser = new LogParser();
    $('crash-log').textContent = (s.lastLines ?? []).flatMap((l) => parser.push(l)).join('\n');
    $('crash-box').hidden = false;
    showPage('home');
  }
});
$('crash-close').addEventListener('click', () => ($('crash-box').hidden = true));

// ---------- Startseite: News + Server ----------
const NEWS_IMAGES: Record<string, string> = {
  versions: 'assets/news/versions.jpg',
  capes: 'assets/news/capes.jpg',
  wings: 'assets/news/cosmetics.jpg',
  hud: 'assets/news/hud.jpg',
  globe: 'assets/news/client.jpg',
  moon: 'assets/news/client.jpg',
  cosmetics: 'assets/news/cosmetics.jpg',
};

function renderNews(): void {
  const grid = $('news-grid');
  grid.replaceChildren(
    ...newsItems.slice(0, 3).map((n) => {
      const image = h('div', { className: 'news-image' }, h('span', { className: 'news-tag', textContent: t(`news.${n.tag}` as I18nKey) }));
      image.style.backgroundImage = `url(${NEWS_IMAGES[n.image] ?? NEWS_IMAGES.moon})`;
      const date = new Date(n.date).toLocaleDateString(lang, { day: 'numeric', month: 'long', year: 'numeric' });
      return h(
        'article',
        { className: 'news-card glass' },
        image,
        h(
          'div',
          { className: 'news-body' },
          h('h3', { textContent: n.title[lang] ?? n.title.en ?? '' }),
          h('p', { textContent: n.text[lang] ?? n.text.en ?? '' }),
          h('span', { className: 'news-date', textContent: date }),
        ),
      );
    }),
  );
}

function serverIcon(s: ServerInfo): HTMLElement {
  return s.icon ? h('img', { className: 'server-icon', src: s.icon, alt: '' }) : h('div', { className: 'server-icon' });
}

function playersLine(s: ServerInfo): HTMLElement {
  return h(
    'div',
    { className: 'server-players' },
    h('span', { className: `status-dot${s.online ? '' : ' off'}` }),
    s.online ? t('servers.online', { count: s.players.toLocaleString(lang) }) : t('servers.offline'),
  );
}

function joinButton(s: ServerInfo): HTMLButtonElement {
  const b = h('button', { type: 'button', className: 'join-button', textContent: t('servers.join'), disabled: !s.online || game.running });
  b.addEventListener('click', () => void launch(s.address));
  return b;
}

function renderServers(): void {
  const featured = [...servers].filter((s) => s.featured).sort((a, b) => b.players - a.players);
  $('server-mini-list').replaceChildren(
    ...featured.slice(0, 9).map((s) => {
      const tile = h(
        'button',
        {
          type: 'button',
          className: 'server-tile',
          title: `${s.name} · ${s.online ? t('servers.online', { count: s.players.toLocaleString(lang) }) : t('servers.offline')}`,
          disabled: !s.online || game.running,
        },
        serverIcon(s),
      );
      tile.addEventListener('click', () => void launch(s.address));
      return tile;
    }),
  );
  $('server-grid').replaceChildren(
    ...[...servers]
      .sort((a, b) => Number(b.featured) - Number(a.featured) || b.players - a.players)
      .map((s) => {
        const actions = h('div', { className: 'result-actions' });
        if (!s.featured) {
          const remove = h('button', { type: 'button', className: 'icon-button danger', title: t('servers.remove') }, icon(ICON.trash));
          remove.addEventListener('click', async () => {
            settings = await mv.saveSettings({ customServers: settings.customServers.filter((a) => a !== s.address) });
            await loadServers(true);
          });
          actions.append(remove);
        }
        actions.append(joinButton(s));
        return h(
          'div',
          { className: 'server-card glass' },
          h(
            'div',
            { className: 'server-card-head' },
            serverIcon(s),
            h(
              'div',
              { className: 'server-info' },
              h('div', { className: 'server-name', textContent: s.name }),
              h('div', { className: 'server-address', textContent: s.address }),
            ),
            s.featured ? h('span', { className: 'featured-badge', textContent: t('servers.featured') }) : null,
          ),
          h('p', { className: 'motd', textContent: s.motd }),
          h('div', { className: 'server-card-foot' }, playersLine(s), actions),
        );
      }),
  );
}

let serversLoaded = 0;
async function loadServers(force = false): Promise<void> {
  if (!force && Date.now() - serversLoaded < 60_000 && servers.length) return;
  serversLoaded = Date.now();
  servers = await mv.servers().catch(() => []);
  renderServers();
}

$('add-server').addEventListener('submit', async (e) => {
  e.preventDefault();
  const input = $<HTMLInputElement>('server-address');
  const address = input.value.trim().toLowerCase();
  if (!/^[a-z0-9.-]+(:\d{1,5})?$/.test(address) || settings.customServers.includes(address)) return;
  settings = await mv.saveSettings({ customServers: [...settings.customServers, address] });
  input.value = '';
  await loadServers(true);
});

// ---------- Konsole ----------
const consoleParser = new LogParser();
const consoleLines: string[] = [];
let consoleScheduled = false;
mv.onGameLog((line) => {
  consoleLines.push(...consoleParser.push(line));
  if (consoleLines.length > 1000) consoleLines.splice(0, consoleLines.length - 1000);
  if (consoleScheduled) return;
  consoleScheduled = true;
  requestAnimationFrame(() => {
    consoleScheduled = false;
    const pre = $('console');
    const atBottom = pre.scrollTop + pre.clientHeight >= pre.scrollHeight - 20;
    pre.textContent = consoleLines.join('\n');
    if (atBottom) pre.scrollTop = pre.scrollHeight;
  });
});
$('console-clear').addEventListener('click', () => {
  consoleLines.length = 0;
  $('console').textContent = '';
});

// ---------- Accounts ----------
function currentAccount() {
  return accounts?.accounts.find((a) => a.id === accounts.selectedId);
}

function renderAccounts(): void {
  if (!accounts) return;
  const current = currentAccount();
  $('account-name').textContent = current ? current.name : t('account.login');
  $<HTMLImageElement>('account-avatar').src = current ? avatarUrl(current.uuid) : '';
  $('welcome-line').textContent = current ? t('home.welcome', { name: '' }).replace(/[,\s]+$/, '') + ',' : t('home.welcomeGuest');
  $('welcome-name').textContent = current ? current.name : '';
  $('nameplate').replaceChildren(h('span', { className: 'dot' }), current ? current.name : 'Moonvibe');

  $('account-list').replaceChildren(
    ...accounts.accounts.map((a) => {
      const remove = h('button', { className: 'icon-button danger', type: 'button', title: t('account.remove') }, icon(ICON.trash));
      remove.addEventListener('click', async (ev) => {
        ev.stopPropagation();
        accounts = await mv.removeAccount(a.id);
        renderAccounts();
        accountChanged();
      });
      const row = h(
        'div',
        { className: `account-row${a.id === accounts.selectedId ? ' selected' : ''}` },
        h('img', { className: 'avatar', src: avatarUrl(a.uuid), alt: '' }),
        h('div', { className: 'account-info' }, a.name, h('small', { textContent: t(a.type === 'microsoft' ? 'account.typeMicrosoft' : 'account.typeOffline') })),
        remove,
      );
      row.addEventListener('click', async () => {
        accounts = await mv.selectAccount(a.id);
        renderAccounts();
        accountChanged();
      });
      return row;
    }),
  );
  $('offline-form').hidden = !accounts.offlineAllowed;
}

function openAccountModal(): void {
  showError($('account-error'), null);
  $('account-modal').hidden = false;
}

$('account-chip').addEventListener('click', openAccountModal);
$('account-close').addEventListener('click', () => ($('account-modal').hidden = true));
$('account-modal').addEventListener('click', (e) => {
  if (e.target === $('account-modal')) $('account-modal').hidden = true;
});

$('ms-login').addEventListener('click', async () => {
  const button = $<HTMLButtonElement>('ms-login');
  button.disabled = true;
  showError($('account-error'), null);
  try {
    accounts = await mv.loginMicrosoft();
    renderAccounts();
    accountChanged();
    $('account-modal').hidden = true;
    toast(t('account.loggedIn'));
  } catch (e) {
    showError($('account-error'), e);
  } finally {
    button.disabled = false;
  }
});

$('offline-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  try {
    accounts = await mv.addOffline($<HTMLInputElement>('offline-name').value.trim());
    $<HTMLInputElement>('offline-name').value = '';
    renderAccounts();
    accountChanged();
  } catch (err) {
    showError($('account-error'), err);
  }
});

// ---------- Mods ----------
let modsType: ProjectType = 'mod';
let modsCategory: string | null = null;
/** null = automatisch (Relevanz beim Suchen, sonst Downloads) */
let modsSort: SearchSort | null = null;

// Modrinth-Kategorien je Projekttyp, mit eigenem Symbol (24er-Raster, Pfade mit | getrennt)
const SWORD = 'M20 4l-9 9|M20 4h-5|M20 4v5|M7 13l4 4|M5 15l4 4|M4 20l3-3';
const SPARKLE = 'M12 3v4|M12 17v4|M3 12h4|M17 12h4|M6 6l2.5 2.5|M15.5 15.5L18 18|M6 18l2.5-2.5|M15.5 8.5L18 6';
const WRENCH = 'M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2.4-.6-.6-2.4z';
const SLIDERS = 'M4 6h9|M17 6h3|M4 12h3|M11 12h9|M4 18h11|M19 18h1|M15 4v4|M9 10v4|M17 16v4';
const CAMERA = 'M4 8h4l2-3h4l2 3h4v11H4z|M12 16a3 3 0 1 0 0-6a3 3 0 0 0 0 6';
const ZAP = 'M13 2L4 14h7l-1 8 9-12h-7z';
const CATEGORIES: Record<ProjectType, [string, string][]> = {
  mod: [
    ['optimization', ZAP],
    ['utility', WRENCH],
    ['decoration', SPARKLE],
    ['adventure', 'M12 3a9 9 0 1 0 0 18a9 9 0 0 0 0-18|M15.5 8.5l-2 5-5 2 2-5z'],
    ['technology', 'M7 7h10v10H7z|M10 3v4|M14 3v4|M10 17v4|M14 17v4|M3 10h4|M3 14h4|M17 10h4|M17 14h4'],
    ['magic', 'M4 20L15 9|M15 3v2|M15 13v2|M21 9h-2|M11 9H9|M19 5l-1.4 1.4|M12.4 11.6L11 13|M19 13l-1.4-1.4'],
    ['storage', 'M3 8l9-5 9 5v8l-9 5-9-5z|M3 8l9 5 9-5|M12 13v8'],
    ['food', 'M12 7c-2-2-7-1.5-7 4 0 4 3 9 5 9 1 0 1.5-.5 2-.5s1 .5 2 .5c2 0 5-5 5-9 0-5.5-5-6-7-4z|M12 7c0-2 1-3.5 3-4'],
    ['game-mechanics', SLIDERS],
    ['worldgen', 'M3 20l6-10 4 6 3-4 5 8z|M15 7a2 2 0 1 0 0-.1'],
    ['equipment', SWORD],
    ['mobs', 'M6 10a2 2 0 1 0 0-4a2 2 0 0 0 0 4|M18 10a2 2 0 1 0 0-4a2 2 0 0 0 0 4|M9 6a2 2 0 1 0 0-4a2 2 0 0 0 0 4|M15 6a2 2 0 1 0 0-4a2 2 0 0 0 0 4|M12 11c-4 0-6 4-6 7 0 2 2 3 6 3s6-1 6-3c0-3-2-7-6-7z'],
    ['transportation', 'M3 7h18l-2 8H5z|M7 19a1.5 1.5 0 1 0 0-.1|M17 19a1.5 1.5 0 1 0 0-.1'],
    ['social', 'M9 11a3.5 3.5 0 1 0 0-7a3.5 3.5 0 0 0 0 7|M2 20c0-3.5 3-6 7-6s7 2.5 7 6|M16 4a3.5 3.5 0 0 1 0 7|M22 20c0-2.6-1.6-4.7-4-5.6'],
    ['library', 'M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2z|M4 21V5|M8 7h7'],
  ],
  resourcepack: [
    ['combat', SWORD],
    ['simplistic', 'M12 4a8 8 0 1 0 0 16a8 8 0 0 0 0-16'],
    ['vanilla-like', 'M12 3l8 4.5v9L12 21l-8-4.5v-9z|M12 12l8-4.5|M12 12v9|M12 12L4 7.5'],
    ['realistic', CAMERA],
    ['themed', 'M12 3a9 9 0 0 0 0 18c1.5 0 2-1 2-2s-1-2 0-3 2-.5 3-.5a4 4 0 0 0 4-4c0-4.5-4-8.5-9-8.5|M7.5 11h.01|M10.5 7h.01|M15.5 8h.01'],
    ['decoration', SPARKLE],
    ['tweaks', SLIDERS],
    ['utility', WRENCH],
  ],
  shader: [
    ['low', ZAP],
    ['vanilla-like', 'M12 3l8 4.5v9L12 21l-8-4.5v-9z|M12 12l8-4.5|M12 12v9|M12 12L4 7.5'],
    ['semi-realistic', 'M12 8a4 4 0 1 0 0 8a4 4 0 0 0 0-8|M12 2v2|M12 20v2|M2 12h2|M20 12h2|M5 5l1.5 1.5|M17.5 17.5L19 19|M5 19l1.5-1.5|M17.5 6.5L19 5'],
    ['realistic', CAMERA],
    ['fantasy', 'M12 3l2.6 5.6 6.1.7-4.5 4.2 1.2 6L12 16.6 6.6 19.5l1.2-6-4.5-4.2 6.1-.7z'],
    ['cartoon', 'M12 3a9 9 0 1 0 0 18a9 9 0 0 0 0-18|M8 14c1 1.5 2.3 2 4 2s3-.5 4-2|M9 9.5h.01|M15 9.5h.01'],
  ],
};

const sortPicker = new Dropdown($('mods-sort'), { className: 'sort-dropdown', align: 'right' });
const SORTS: SearchSort[] = ['relevance', 'downloads', 'follows', 'newest', 'updated'];

function effectiveSort(): SearchSort {
  return modsSort ?? ($<HTMLInputElement>('mods-search').value.trim() ? 'relevance' : 'downloads');
}

function renderModFilters(): void {
  sortPicker.setOptions(SORTS.map((value) => ({ value, label: t(`mods.sort.${value}` as I18nKey) })), effectiveSort());
  const chip = (id: string | null, path: string) => {
    const b = h(
      'button',
      { type: 'button', className: `cat-chip${modsCategory === id ? ' active' : ''}` },
      icon(path),
      t(`mods.cat.${id ?? 'all'}` as I18nKey),
    );
    b.addEventListener('click', () => {
      modsCategory = id;
      renderModFilters();
      void runSearch();
    });
    return b;
  };
  $('mods-categories').replaceChildren(
    chip(null, 'M4 4h7v7H4z|M13 4h7v7h-7z|M4 13h7v7H4z|M13 13h7v7h-7z'),
    ...CATEGORIES[modsType].map(([id, path]) => chip(id, path)),
  );
}
sortPicker.change((value) => {
  modsSort = value as SearchSort;
  void runSearch();
});
let searchOffset = 0;
let searchToken = 0;
let installed: InstalledItem[] = [];

const modsVersion = () => modsVersionPicker.value;

function projectIcon(url: string | null): HTMLElement {
  return url ? h('img', { src: url, alt: '', loading: 'lazy' }) : h('div', { className: 'icon-fallback' });
}

function renderInstalled(): void {
  const list = $('mods-installed');
  if (installed.length === 0) {
    list.replaceChildren(h('div', { className: 'empty', textContent: t('mods.nothingInstalled') }));
    return;
  }
  list.replaceChildren(
    ...installed.map((item) => {
      const actions = h('div', { className: 'result-actions' });
      if (item.source !== 'moonvibe') {
        const toggle = h('button', { className: 'icon-button', type: 'button', title: t(item.enabled ? 'mods.disable' : 'mods.enable') }, icon(ICON.power));
        toggle.addEventListener('click', async () => {
          await mv.toggleInstalled(modsVersion(), modsType, item.file);
          await refreshInstalled();
        });
        const remove = h('button', { className: 'icon-button danger', type: 'button', title: t('mods.delete') }, icon(ICON.trash));
        remove.addEventListener('click', async () => {
          await mv.removeInstalled(modsVersion(), modsType, item.file);
          await refreshInstalled();
        });
        actions.append(toggle, remove);
      }
      const tag =
        item.source === 'pack'
          ? h('span', { className: 'tag', textContent: t('mods.tagPack') })
          : item.source === 'moonvibe'
            ? h('span', { className: 'tag brand', textContent: t('mods.tagMoonvibe') })
            : null;
      return h(
        'div',
        { className: `result${item.enabled ? '' : ' disabled'}`, title: item.file },
        item.source === 'moonvibe' ? h('img', { src: 'assets/icon-64.png', alt: '' }) : projectIcon(item.iconUrl),
        h('div', { className: 'result-body' }, h('div', { className: 'result-title', textContent: item.title }), tag),
        actions,
      );
    }),
  );
}

async function refreshInstalled(): Promise<void> {
  if (!modsVersion()) return;
  installed = await mv.installed(modsVersion(), modsType).catch(() => []);
  renderInstalled();
  document.querySelectorAll<HTMLButtonElement>('#mods-results [data-project]').forEach((b) => {
    const done = installed.some((i) => i.projectId === b.dataset.project);
    b.disabled = done;
    b.textContent = t(done ? 'mods.installed' : 'mods.install');
  });
}

function resultRow(hit: SearchHit): HTMLElement {
  const done = installed.some((i) => i.projectId === hit.projectId);
  const button = h('button', { className: 'primary-small', type: 'button', disabled: done, textContent: t(done ? 'mods.installed' : 'mods.install') });
  button.dataset.project = hit.projectId;
  button.addEventListener('click', async () => {
    button.disabled = true;
    button.textContent = t('mods.installing');
    try {
      await mv.install(modsVersion(), hit.projectId, modsType);
      toast(t('mods.installedToast', { name: hit.title }));
    } catch (e) {
      toast(errorText(e));
    }
    await refreshInstalled();
  });
  return h(
    'div',
    { className: 'result' },
    projectIcon(hit.iconUrl),
    h(
      'div',
      { className: 'result-body' },
      h('a', { href: `https://modrinth.com/project/${hit.slug}`, target: '_blank', textContent: hit.title, className: 'result-title' }),
      h('div', { className: 'result-desc', textContent: hit.description }),
      h('span', { className: 'tag', textContent: `${hit.author} · ${t('mods.downloads', { count: formatNumber(hit.downloads) })}` }),
    ),
    button,
  );
}

async function runSearch(append = false): Promise<void> {
  if (!modsVersion()) return;
  const token = ++searchToken;
  if (!append) searchOffset = 0;
  const results = $('mods-results');
  if (!append) results.replaceChildren(h('div', { className: 'empty', textContent: t('mods.searching') }));
  try {
    const res = await mv.search({
      query: $<HTMLInputElement>('mods-search').value.trim(),
      type: modsType,
      gameVersion: modsVersion(),
      offset: searchOffset,
      category: modsCategory,
      sort: effectiveSort(),
    });
    if (token !== searchToken) return;
    if (!append) results.replaceChildren();
    results.append(...res.hits.map(resultRow));
    if (!append && res.hits.length === 0) results.append(h('div', { className: 'empty', textContent: t('mods.noResults') }));
    searchOffset += res.hits.length;
    $('mods-more').hidden = searchOffset >= res.total;
  } catch (e) {
    if (token === searchToken) results.replaceChildren(h('div', { className: 'empty', textContent: errorText(e) }));
  }
}

// Shader laufen nur mit der Mod Iris – die gibt es erst ab 1.16.5
const IRIS_PROJECT_ID = 'YL57xq9U';
/** none = kein Hinweis, iris = Iris fehlt noch (Ein-Klick-Installation), unavailable = gibt es für diese Version nicht */
let shaderState: 'none' | 'iris' | 'unavailable' = 'none';

function renderShaderHint(): void {
  const el = $('shader-hint');
  el.hidden = modsType !== 'shader' || shaderState === 'none';
  // ohne Iris keine Suche – Suchfeld und Kategorien wären nutzlos
  $('page-mods').classList.toggle('no-shaders', modsType === 'shader' && shaderState === 'unavailable');
  if (shaderState === 'unavailable') {
    el.replaceChildren(t('mods.shaderUnavailable'));
    return;
  }
  const button = h('button', { type: 'button', className: 'primary-small', textContent: t('mods.installIris') });
  button.addEventListener('click', async () => {
    const version = modsVersion();
    button.disabled = true;
    button.textContent = t('mods.installing');
    try {
      await mv.install(version, IRIS_PROJECT_ID, 'mod');
      toast(t('mods.installedToast', { name: 'Iris' }));
    } catch (e) {
      toast(errorText(e));
    }
    await checkShaders();
  });
  el.replaceChildren(h('span', { textContent: t('mods.shaderHint') }), button);
}

/** Gehen Shader auf der gewählten Version – und ist Iris schon installiert? */
async function checkShaders(): Promise<boolean> {
  const version = modsVersion();
  const supported = await mv.shadersSupported(version).catch(() => true);
  const mods = supported ? await mv.installed(version, 'mod').catch(() => []) : [];
  if (version !== modsVersion()) return supported;
  shaderState = !supported ? 'unavailable' : mods.some((m) => m.projectId === IRIS_PROJECT_ID) ? 'none' : 'iris';
  renderShaderHint();
  return supported;
}

let modsRefresh = 0;
async function refreshMods(): Promise<void> {
  const token = ++modsRefresh;
  shaderState = 'none';
  renderShaderHint();
  renderModFilters();
  await refreshInstalled();
  if (modsType === 'shader') {
    const supported = await checkShaders();
    if (token !== modsRefresh) return;
    if (!supported) {
      // ohne Iris keine Shader-Liste – die könnte man ja doch nicht benutzen
      searchToken++;
      $('mods-results').replaceChildren();
      $('mods-more').hidden = true;
      return;
    }
  }
  if (token === modsRefresh) await runSearch();
}

let searchTimer = 0;
$('mods-search').addEventListener('input', () => {
  clearTimeout(searchTimer);
  searchTimer = window.setTimeout(() => {
    sortPicker.value = effectiveSort();
    void runSearch();
  }, 350);
});
$('mods-more').addEventListener('click', () => void runSearch(true));
modsVersionPicker.change(() => void refreshMods());
document.querySelectorAll<HTMLElement>('#mods-tabs .tab').forEach((tab) =>
  tab.addEventListener('click', () => {
    modsType = tab.dataset.type as ProjectType;
    modsCategory = null;
    document.querySelectorAll('#mods-tabs .tab').forEach((x) => x.classList.toggle('active', x === tab));
    void refreshMods();
  }),
);
$('open-instance').addEventListener('click', () => mv.openFolder('instance', modsVersion()).catch((e) => toast(errorText(e))));

// ---------- Cosmetics ----------
const SLOT_LABEL: Record<CosmeticSlot, I18nKey> = {
  cape: 'cosmetics.tabCape',
  hat: 'cosmetics.tabHat',
  face: 'cosmetics.tabFace',
  back: 'cosmetics.tabBack',
  wings: 'cosmetics.tabWings',
  shoes: 'cosmetics.tabShoes',
  pet: 'cosmetics.tabPet',
};

let cosmetics: CosmeticsState | null = null;
let slotTab: CosmeticSlot = 'cape';
let preview: PlayerPreview | null = null;
let homePreview: PlayerPreview | null = null;
let skinVersion = 0;
const appliedSkin = new WeakMap<PlayerPreview, number>();
const models = new Map<string, Promise<ModelJson>>();
const thumbnails = new Map<string, string>();
let thumbQueue: Promise<void> = Promise.resolve();
const previewTokens = new WeakMap<PlayerPreview, Record<string, number>>();

function accountChanged(): void {
  skinVersion++;
  void refreshCosmetics();
}

function cosmeticName(c: Cosmetic): string {
  if (c.custom) return t('cosmetics.customCape');
  return c.names[lang] ?? c.names.en ?? c.id;
}

function findCosmetic(id: string | null): Cosmetic | undefined {
  return id ? cosmetics?.catalog.find((c) => c.id === id) : undefined;
}

function model(id: string): Promise<ModelJson> {
  let m = models.get(id);
  if (!m) {
    m = mv.cosmeticModel(id) as Promise<ModelJson>;
    models.set(id, m);
    m.catch(() => models.delete(id));
  }
  return m;
}

/** Zeigt ein Cosmetic auf einer 3D-Figur (oder entfernt es bei id = null). */
async function showOn(target: PlayerPreview | null, slot: CosmeticSlot, id: string | null): Promise<void> {
  if (!target) return;
  const tokens = previewTokens.get(target) ?? {};
  previewTokens.set(target, tokens);
  const token = (tokens[slot] = (tokens[slot] ?? 0) + 1);
  const item = findCosmetic(id);
  try {
    if (slot === 'cape') {
      if (token === tokens[slot]) await target.setCape(item?.textureUrl ?? null);
    } else {
      const json = item?.modelUrl ? await model(item.id) : null;
      if (token === tokens[slot]) await target.setModel(slot, json, item?.textureUrl ?? null);
    }
  } catch {
    // Vorschau ist nicht kritisch
  }
}

async function syncPreview(target: PlayerPreview | null): Promise<void> {
  if (!target) return;
  if (appliedSkin.get(target) !== skinVersion) {
    appliedSkin.set(target, skinVersion);
    await target.setSkin(await mv.skin().catch(() => ({ dataUrl: null, slim: false })));
  }
  for (const slot of SLOTS) void showOn(target, slot, cosmetics?.equipped[slot] ?? null);
}

function thumbnail(c: Cosmetic, img: HTMLImageElement): void {
  if (c.thumbnailUrl) {
    img.src = c.thumbnailUrl;
    return;
  }
  const cached = thumbnails.get(c.id);
  if (cached) {
    img.src = cached;
    return;
  }
  // Kein Vorschaubild vom Server → selbst rendern (nacheinander, teilt sich einen WebGL-Kontext)
  thumbQueue = thumbQueue.then(async () => {
    try {
      const json = c.modelUrl ? await model(c.id) : null;
      const url = await renderThumbnail(c.type, c.textureUrl, json);
      thumbnails.set(c.id, url);
      img.src = url;
    } catch {
      // ohne Vorschaubild weiter
    }
  });
}

function renderCategoryTabs(): void {
  const tabs = $('cosmetic-tabs');
  tabs.replaceChildren(
    ...SLOTS.map((slot) => {
      const count = cosmetics?.catalog.filter((c) => c.type === slot).length ?? 0;
      const b = h(
        'button',
        { type: 'button', className: `category${slot === slotTab ? ' active' : ''}` },
        icon(ICON[slot]),
        t(SLOT_LABEL[slot]),
        h('span', { className: 'count', textContent: String(count) }),
      );
      b.dataset.slot = slot;
      b.addEventListener('click', () => {
        slotTab = slot;
        preview?.face(BACK_SLOTS.includes(slot));
        renderCosmetics();
      });
      return b;
    }),
  );
}

function renderWearing(): void {
  const chips: HTMLElement[] = [];
  for (const slot of SLOTS) {
    const item = findCosmetic(cosmetics?.equipped[slot] ?? null);
    if (item) chips.push(h('span', { className: 'chip', textContent: cosmeticName(item) }));
  }
  $('wearing').replaceChildren(...chips);
}

function renderCosmetics(): void {
  if (!cosmetics) return;
  const state = cosmetics;
  renderCategoryTabs();
  showError($('cosmetics-error'), state.error);
  renderWearing();
  const query = $<HTMLInputElement>('cosmetic-search').value.trim().toLowerCase();
  const equippedId = state.equipped[slotTab];
  const card = (item: Cosmetic | null) => {
    const id = item?.id ?? null;
    const thumb = h('div', { className: 'thumb' });
    if (item) {
      const img = h('img', { alt: '', loading: 'lazy' });
      thumb.append(img);
      thumbnail(item, img);
    } else {
      thumb.append(h('div', { className: 'none' }));
    }
    const rarityLabel = item?.custom ? t('cosmetics.custom') : item ? t(`rarity.${item.rarity}` as I18nKey) : null;
    const el = h(
      'button',
      {
        type: 'button',
        className: `cosmetic-card ${item?.custom ? 'custom' : (item?.rarity ?? '')}${equippedId === id ? ' equipped' : ''}`,
      },
      thumb,
      h('span', { className: 'cosmetic-name', textContent: item ? cosmeticName(item) : t('cosmetics.none') }),
      rarityLabel ? h('span', { className: 'rarity', textContent: rarityLabel }) : null,
      item?.custom ? customCapeActions() : null,
    );
    const slot = slotTab;
    el.addEventListener('mouseenter', () => void showOn(preview, slot, id));
    el.addEventListener('mouseleave', () => void showOn(preview, slot, cosmetics?.equipped[slot] ?? null));
    el.addEventListener('click', async () => {
      try {
        cosmetics = await mv.equip(slot, id);
        renderCosmetics();
        void showOn(preview, slot, id);
        void showOn(homePreview, slot, id);
        toast(item ? t('cosmetics.equipped', { name: cosmeticName(item) }) : t('cosmetics.removed'));
      } catch (e) {
        showError($('cosmetics-error'), e);
      }
    });
    return el;
  };
  const rarityOrder = { legendary: 0, epic: 1, rare: 2, common: 3 };
  const items = state.catalog
    .filter((c) => c.type === slotTab)
    .filter((c) => !query || cosmeticName(c).toLowerCase().includes(query))
    .sort((a, b) => rarityOrder[a.rarity] - rarityOrder[b.rarity]);
  const upload = slotTab === 'cape' && !query ? [uploadCard()] : [];
  $('cosmetic-grid').replaceChildren(...(state.online ? [card(null), ...upload, ...items.map(card)] : []));
}

/** Karte "Cape hochladen" – öffnet den Cape-Editor. */
function uploadCard(): HTMLElement {
  const el = h(
    'button',
    { type: 'button', className: 'cosmetic-card upload' },
    h('div', { className: 'thumb' }, h('div', { className: 'upload-plus' }, icon(ICON.plus))),
    h('span', { className: 'cosmetic-name', textContent: t('cosmetics.upload') }),
    h('span', { className: 'rarity', textContent: t('cosmetics.uploadHint') }),
  );
  el.addEventListener('click', () => void pickCapeImage());
  return el;
}

/** Bearbeiten/Löschen-Knöpfe auf der Karte des eigenen Capes (Löschen braucht einen zweiten Klick). */
function customCapeActions(): HTMLElement {
  const edit = h('span', { className: 'icon-button', title: t('cosmetics.editCape'), role: 'button' }, icon(ICON.edit));
  const remove = h('span', { className: 'icon-button danger', title: t('cosmetics.deleteCape'), role: 'button' }, icon(ICON.trash));
  edit.addEventListener('click', (e) => {
    e.stopPropagation();
    void pickCapeImage();
  });
  let armed = 0;
  remove.addEventListener('click', async (e) => {
    e.stopPropagation();
    if (!armed) {
      remove.classList.add('confirm');
      remove.title = t('cosmetics.deleteConfirm');
      toast(t('cosmetics.deleteConfirm'));
      armed = window.setTimeout(() => {
        armed = 0;
        remove.classList.remove('confirm');
        remove.title = t('cosmetics.deleteCape');
      }, 3000);
      return;
    }
    clearTimeout(armed);
    try {
      cosmetics = await mv.removeCustomCape();
      renderCosmetics();
      void showOn(preview, 'cape', cosmetics.equipped.cape);
      void showOn(homePreview, 'cape', cosmetics.equipped.cape);
      toast(t('cosmetics.capeDeleted'));
    } catch (err) {
      showError($('cosmetics-error'), err);
    }
  });
  return h('div', { className: 'card-actions' }, edit, remove);
}

// ---------- Eigenes Cape: Editor ----------
const CAPE_COLORS = ['#0b0f19', '#f4f6fb', '#e11d48', '#f97316', '#facc15', '#22c55e', '#2e9bff', '#8b5cf6', '#ec4899'];
let capeImage: HTMLImageElement | null = null;
let capeOptions: CapeOptions = { fit: 'fill', background: null, inside: 'image', template: false };
let capePreview: PlayerPreview | null = null;
let capeRender = 0;

async function pickCapeImage(): Promise<void> {
  // ohne (passenden) Account erst gar nicht den Editor öffnen
  if (cosmetics?.error) {
    toast(cosmetics.error);
    return;
  }
  let dataUrl: string | null;
  try {
    dataUrl = await mv.pickImage();
  } catch (e) {
    toast(errorText(e));
    return;
  }
  if (!dataUrl) return;
  const image = new Image();
  image.src = dataUrl;
  try {
    await image.decode();
  } catch {
    toast(t('cosmetics.imageError'));
    return;
  }
  capeImage = image;
  capeOptions = {
    ...capeOptions,
    background: null,
    template: looksLikeTemplate(image),
    fit: hasTransparency(image) ? 'contain' : 'fill',
  };
  $<HTMLImageElement>('cape-source').src = dataUrl;
  showError($('cape-error'), null);
  $('cape-modal').hidden = false;
  if (!capePreview) {
    capePreview = new PlayerPreview($<HTMLCanvasElement>('cape-canvas'), { distance: 74, lookAt: 15, yaw: Math.PI - 0.5 });
    await capePreview.setSkin(await mv.skin().catch(() => ({ dataUrl: null, slim: false })));
  }
  capePreview.face(true);
  renderCapeEditor();
}

function renderCapeEditor(): void {
  if (!capeImage) return;
  const image = capeImage;
  $('cape-template-row').hidden = !canUseAsTemplate(image);
  $<HTMLInputElement>('cape-template').checked = capeOptions.template;
  $('cape-options').classList.toggle('disabled', capeOptions.template);
  for (const [id, value] of [['cape-fit', capeOptions.fit], ['cape-inside', capeOptions.inside]] as const) {
    $(id).querySelectorAll<HTMLElement>('button').forEach((b) => b.classList.toggle('active', b.dataset.value === value));
  }
  // Farbfelder: Auto (aus dem Bild), feste Farben, eigene Farbe
  const auto = averageColor(image);
  const swatch = (color: string | null, label?: string) => {
    const b = h('button', {
      type: 'button',
      className: `swatch${color === null ? ' auto' : ''}${capeOptions.background === color ? ' active' : ''}`,
      title: label ?? color ?? '',
      textContent: label ?? '',
    });
    b.style.background = color ?? auto;
    b.addEventListener('click', () => {
      capeOptions.background = color;
      renderCapeEditor();
    });
    return b;
  };
  const custom = h('input', { type: 'color', value: capeOptions.background ?? auto });
  custom.addEventListener('input', () => {
    capeOptions.background = custom.value;
    void updateCapePreview();
  });
  custom.addEventListener('change', () => renderCapeEditor());
  const picker = h('label', { className: `swatch swatch-picker${capeOptions.background && !CAPE_COLORS.includes(capeOptions.background) ? ' active' : ''}` }, custom);
  $('cape-colors').replaceChildren(swatch(null, t('cosmetics.auto')), ...CAPE_COLORS.map((c) => swatch(c)), picker);
  void updateCapePreview();
}

async function updateCapePreview(): Promise<void> {
  if (!capeImage || !capePreview) return;
  const token = ++capeRender;
  const url = buildCapeTexture(capeImage, capeOptions).toDataURL('image/png');
  if (token === capeRender) await capePreview.setCape(url);
}

function closeCapeEditor(): void {
  $('cape-modal').hidden = true;
  capeImage = null;
}

for (const [id, key] of [['cape-fit', 'fit'], ['cape-inside', 'inside']] as const) {
  $(id).querySelectorAll<HTMLElement>('button').forEach((b) =>
    b.addEventListener('click', () => {
      capeOptions = { ...capeOptions, [key]: b.dataset.value };
      renderCapeEditor();
    }),
  );
}
$<HTMLInputElement>('cape-template').addEventListener('change', (e) => {
  capeOptions.template = (e.target as HTMLInputElement).checked;
  renderCapeEditor();
});
$('cape-other').addEventListener('click', () => void pickCapeImage());
$('cape-close').addEventListener('click', closeCapeEditor);
$('cape-cancel').addEventListener('click', closeCapeEditor);
$('cape-modal').addEventListener('click', (e) => {
  if (e.target === $('cape-modal')) closeCapeEditor();
});
$('cape-save').addEventListener('click', async () => {
  if (!capeImage) return;
  const button = $<HTMLButtonElement>('cape-save');
  button.disabled = true;
  button.textContent = t('cosmetics.saving');
  try {
    cosmetics = await mv.uploadCape(await canvasToPng(buildCapeTexture(capeImage, capeOptions)));
    closeCapeEditor();
    slotTab = 'cape';
    renderCosmetics();
    void showOn(preview, 'cape', cosmetics.equipped.cape);
    void showOn(homePreview, 'cape', cosmetics.equipped.cape);
    preview?.face(true);
    toast(t('cosmetics.capeSaved'));
  } catch (e) {
    showError($('cape-error'), e);
  } finally {
    button.disabled = false;
    button.textContent = t('cosmetics.saveCape');
  }
});

$('cosmetic-search').addEventListener('input', () => renderCosmetics());

async function refreshCosmetics(): Promise<void> {
  cosmetics = await mv.cosmetics();
  renderCosmetics();
  void syncPreview(homePreview);
  if (preview) void syncPreview(preview);
}

async function openCosmetics(): Promise<void> {
  preview ??= new PlayerPreview($<HTMLCanvasElement>('preview-canvas'), { distance: 96, lookAt: 17 });
  preview.face(BACK_SLOTS.includes(slotTab));
  await refreshCosmetics();
}

// ---------- Einstellungen ----------
const behaviorPicker = new Dropdown($('settings-behavior'), { align: 'right' });
behaviorPicker.change(async (value) => (settings = await mv.saveSettings({ launcherBehavior: value as Settings['launcherBehavior'] })));

async function initSettings(): Promise<void> {
  const totalMb = await mv.systemMemoryMb();
  const memory = $<HTMLInputElement>('memory');
  memory.max = String(Math.max(2048, Math.floor((totalMb - 1024) / 512) * 512));
  memory.value = String(settings.memoryMb);
  const showMemory = () => ($('memory-value').textContent = `${(Number(memory.value) / 1024).toFixed(1)} GB`);
  showMemory();
  memory.addEventListener('input', showMemory);
  memory.addEventListener('change', async () => (settings = await mv.saveSettings({ memoryMb: Number(memory.value) })));

  const width = $<HTMLInputElement>('game-width');
  const height = $<HTMLInputElement>('game-height');
  width.value = settings.gameWidth ? String(settings.gameWidth) : '';
  height.value = settings.gameHeight ? String(settings.gameHeight) : '';
  const saveSize = async () => {
    settings = await mv.saveSettings({ gameWidth: Number(width.value) || null, gameHeight: Number(height.value) || null });
  };
  width.addEventListener('change', saveSize);
  height.addEventListener('change', saveSize);

  const api = $<HTMLInputElement>('api-url');
  api.value = settings.apiUrl;
  api.placeholder = (await mv.appInfo()).defaultApiUrl || 'https://api.<name>.workers.dev';
  api.addEventListener('change', async () => {
    settings = await mv.saveSettings({ apiUrl: api.value.trim() });
    thumbnails.clear();
    models.clear();
    void refreshCosmetics();
  });

  $('open-data').addEventListener('click', () => mv.openFolder('data'));
}

// ---------- Start ----------
/** Versionsliste laden; ohne Internet (und ohne gemerkte Liste) alle 15 Sekunden neu versuchen. */
async function loadVersions(retry = false): Promise<void> {
  try {
    versions = await mv.getVersions();
    if (!retry) return;
    showError($('play-error'), null);
    renderVersionPickers();
    if (currentPage === 'mods') void refreshMods();
  } catch (e) {
    showError($('play-error'), t('home.versionsError', { error: errorText(e) }));
    setTimeout(() => void loadVersions(true), 15_000);
  }
}

async function init(): Promise<void> {
  const started = performance.now();
  const info = await mv.appInfo();
  $('app-version').textContent = `moonvibe ${info.version}${info.dev ? ' · Dev' : ''}`;
  $('data-dir').textContent = info.dataDir;

  [settings, accounts, game] = await Promise.all([mv.getSettings(), mv.accounts(), mv.gameState()]);
  lang = settings.language;
  await initSettings();
  void loadPanorama();

  await loadVersions();
  applyI18n();

  homePreview = new PlayerPreview($<HTMLCanvasElement>('home-canvas'), { distance: 92, lookAt: 17, yaw: -0.45 });
  void refreshCosmetics();
  void mv.news().then((n) => {
    newsItems = n;
    renderNews();
  });
  void loadServers();

  // Startbildschirm kurz zeigen, dann sanft ausblenden
  const wait = Math.max(0, 900 - (performance.now() - started));
  setTimeout(() => {
    $('splash').classList.add('done');
    moveIndicator();
  }, wait);
}

void init();
