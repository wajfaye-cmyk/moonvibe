// Jede Minecraft-Version bekommt einen eigenen Spielordner (eigene Mods, Welten, Einstellungen).
import AdmZip from 'adm-zip';
import { createHash } from 'node:crypto';
import { existsSync } from 'node:fs';
import { copyFile, mkdir, readdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import type { InstalledItem, Loader, ProjectType } from '../shared/types';
import { downloadFile } from './http';
import { getProject, latestVersion, loadersFor, primaryFile, resolveWithDependencies, type ModrinthVersion } from './modrinth';

const FOLDERS: Record<ProjectType, string> = { mod: 'mods', resourcepack: 'resourcepacks', shader: 'shaderpacks' };

/** Mods, die moonvibe automatisch mitinstalliert (Performance + Grundlagen). */
const PACK: Record<Loader, string[]> = {
  fabric: ['fabric-api', 'sodium', 'lithium', 'ferrite-core', 'modmenu'],
  'legacy-fabric': ['legacy-fabric-api'],
};

interface IndexEntry {
  projectId: string | null;
  versionId: string | null;
  title: string;
  iconUrl: string | null;
  source: InstalledItem['source'];
}

interface InstanceIndex {
  files: Record<string, IndexEntry>;
  packCheckedAt?: number;
}

export function instanceDir(dataDir: string, mcVersion: string): string {
  return join(dataDir, 'instances', mcVersion);
}

/**
 * Neue Spielordner bekommen eine options.txt mit der Sprache des Launchers und ohne
 * Tutorial-/Einführungshinweise. Bestehende Einstellungen werden nie überschrieben.
 */
export async function writeDefaultOptions(dir: string, minecraftLang: string): Promise<void> {
  const file = join(dir, 'options.txt');
  if (existsSync(file)) return;
  await mkdir(dir, { recursive: true });
  await writeFile(file, `lang:${minecraftLang}\ntutorialStep:none\nonboardAccessibility:false\n`);
}

const baseName = (file: string) => file.replace(/\.disabled$/, '');

class Instance {
  private indexFile: string;

  constructor(
    readonly dir: string,
    readonly mcVersion: string,
    readonly loader: Loader | null,
  ) {
    this.indexFile = join(dir, 'moonvibe-index.json');
  }

  async readIndex(): Promise<InstanceIndex> {
    try {
      return JSON.parse(await readFile(this.indexFile, 'utf8')) as InstanceIndex;
    } catch {
      return { files: {} };
    }
  }

  async writeIndex(index: InstanceIndex): Promise<void> {
    await mkdir(this.dir, { recursive: true });
    await writeFile(this.indexFile, JSON.stringify(index, null, 2));
  }

  folder(type: ProjectType): string {
    return join(this.dir, FOLDERS[type]);
  }

  async list(type: ProjectType): Promise<InstalledItem[]> {
    const index = await this.readIndex();
    const files = await readdir(this.folder(type)).catch(() => [] as string[]);
    return files
      .filter((f) => /\.(jar|zip)(\.disabled)?$/.test(f))
      .map((file) => {
        const meta = index.files[baseName(file)];
        return {
          file,
          title: meta?.title ?? baseName(file),
          enabled: !file.endsWith('.disabled'),
          source: meta?.source ?? 'unknown',
          projectId: meta?.projectId ?? null,
          iconUrl: meta?.iconUrl ?? null,
        };
      })
      .sort((a, b) => a.title.localeCompare(b.title));
  }

  /** Entfernt alle Dateien eines Projekts (alte Versionen beim Update). */
  private async removeProject(index: InstanceIndex, type: ProjectType, projectId: string): Promise<boolean> {
    let disabled = false;
    for (const [file, meta] of Object.entries(index.files)) {
      if (meta.projectId !== projectId) continue;
      const path = join(this.folder(type), file);
      if (existsSync(`${path}.disabled`)) disabled = true;
      await rm(path, { force: true });
      await rm(`${path}.disabled`, { force: true });
      delete index.files[file];
    }
    return disabled;
  }

  private async installVersions(type: ProjectType, versions: ModrinthVersion[], source: IndexEntry['source']): Promise<void> {
    const index = await this.readIndex();
    for (const version of versions) {
      const file = primaryFile(version);
      const current = Object.values(index.files).find((m) => m.projectId === version.project_id);
      if (current?.versionId === version.id) continue;

      const project = await getProject(version.project_id).catch(() => null);
      // Abhängigkeiten, die der Nutzer schon selbst installiert hat, behalten ihre Herkunft.
      const keepSource = current && current.source === 'user' ? 'user' : source;
      const wasDisabled = await this.removeProject(index, type, version.project_id);
      const target = join(this.folder(type), file.filename);
      await downloadFile({ url: file.url, path: target, sha1: file.hashes.sha1, size: file.size });
      if (wasDisabled) await rename(target, `${target}.disabled`);
      index.files[file.filename] = {
        projectId: version.project_id,
        versionId: version.id,
        title: project?.title ?? file.filename,
        iconUrl: project?.icon_url ?? null,
        source: keepSource,
      };
    }
    await this.writeIndex(index);
  }

  async install(type: ProjectType, projectId: string): Promise<void> {
    const versions = await resolveWithDependencies(projectId, this.mcVersion, loadersFor(type, this.loader));
    // Das gewählte Projekt zählt als "user", mitgezogene Abhängigkeiten ebenfalls.
    await this.installVersions(type, versions, 'user');
  }

  async remove(type: ProjectType, file: string): Promise<void> {
    const index = await this.readIndex();
    await rm(join(this.folder(type), file), { force: true });
    delete index.files[baseName(file)];
    await this.writeIndex(index);
  }

  async toggle(type: ProjectType, file: string): Promise<void> {
    const path = join(this.folder(type), file);
    await rename(path, file.endsWith('.disabled') ? path.replace(/\.disabled$/, '') : `${path}.disabled`);
  }

  /**
   * Hält das moonvibe-Paket aktuell: Performance-Mods von Modrinth + unsere eigene Mod.
   * Ohne Internet wird einfach mit dem gestartet, was schon da ist.
   */
  async syncPack(clientModsDir: string, log: (msg: string) => void): Promise<void> {
    if (!this.loader) return;
    await mkdir(this.folder('mod'), { recursive: true });
    await this.syncOwnMod(clientModsDir);

    const index = await this.readIndex();
    const hourAgo = Date.now() - 60 * 60 * 1000;
    if (index.packCheckedAt && index.packCheckedAt > hourAgo) return;

    const loaders = loadersFor('mod', this.loader);
    const versions: ModrinthVersion[] = [];
    for (const slug of PACK[this.loader]) {
      try {
        const v = await latestVersion(slug, this.mcVersion, loaders);
        if (v) versions.push(v);
      } catch (e) {
        log(`[moonvibe] Could not check ${slug}: ${(e as Error).message}`);
      }
    }
    try {
      await this.installVersions('mod', versions, 'pack');
      const updated = await this.readIndex();
      updated.packCheckedAt = Date.now();
      await this.writeIndex(updated);
    } catch (e) {
      log(`[moonvibe] Pack update failed: ${(e as Error).message}`);
    }
  }

  /** Kopiert die mitgelieferte moonvibe-Mod (falls es eine für diese Version gibt) in den mods-Ordner. */
  private async syncOwnMod(clientModsDir: string): Promise<void> {
    const sourceDir = join(clientModsDir, this.mcVersion);
    const jar = (await readdir(sourceDir).catch(() => [] as string[])).find((f) => f.endsWith('.jar'));
    const index = await this.readIndex();
    const old = Object.entries(index.files).filter(([, m]) => m.source === 'moonvibe');

    if (!jar) {
      for (const [file] of old) {
        await rm(join(this.folder('mod'), file), { force: true });
        delete index.files[file];
      }
      await this.writeIndex(index);
      return;
    }

    const source = join(sourceDir, jar);
    const target = join(this.folder('mod'), jar);
    const sha1 = async (p: string) => createHash('sha1').update(await readFile(p)).digest('hex');
    if (existsSync(target) && (await sha1(source)) === (await sha1(target))) return;

    for (const [file] of old) {
      await rm(join(this.folder('mod'), file), { force: true });
      delete index.files[file];
    }
    await copyFile(source, target);
    index.files[jar] = { projectId: 'moonvibe', versionId: null, title: 'moonvibe', iconUrl: null, source: 'moonvibe' };
    await this.writeIndex(index);
  }
}

export function openInstance(dataDir: string, mcVersion: string, loader: Loader | null): Instance {
  return new Instance(instanceDir(dataDir, mcVersion), mcVersion, loader);
}

/** Mindest-Java der moonvibe-Mod für eine Version (aus ihrer fabric.mod.json, 0 = keine Angabe). */
export async function moonvibeJava(clientModsDir: string, mcVersion: string): Promise<number> {
  const dir = join(clientModsDir, mcVersion);
  const jar = (await readdir(dir).catch(() => [] as string[])).find((f) => f.endsWith('.jar'));
  if (!jar) return 0;
  try {
    const entry = new AdmZip(join(dir, jar)).getEntry('fabric.mod.json');
    const java = JSON.parse(entry?.getData().toString('utf8') ?? '{}')?.depends?.java;
    return Number(/(\d+)/.exec(String(java ?? ''))?.[1] ?? 0);
  } catch {
    return 0;
  }
}

/** Versionen, für die der Launcher eine eigene moonvibe-Mod mitbringt. */
export async function moonvibeVersions(clientModsDir: string): Promise<Set<string>> {
  const dirs = await readdir(clientModsDir, { withFileTypes: true }).catch(() => []);
  const result = new Set<string>();
  for (const d of dirs) {
    if (!d.isDirectory()) continue;
    const files = await readdir(join(clientModsDir, d.name)).catch(() => [] as string[]);
    if (files.some((f) => f.endsWith('.jar'))) result.add(d.name);
  }
  return result;
}
