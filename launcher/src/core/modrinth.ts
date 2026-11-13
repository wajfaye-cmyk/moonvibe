// Modrinth-API: Mods, Resourcepacks und Shader suchen und passende Dateien finden.
import type { Loader, ProjectType, SearchParams, SearchResult } from '../shared/types';
import { LocalizedError } from '../shared/i18n';
import { fetchJson, HttpError } from './http';

const API = 'https://api.modrinth.com/v2';

export interface ModrinthVersion {
  id: string;
  project_id: string;
  version_number: string;
  game_versions: string[];
  loaders: string[];
  files: { url: string; filename: string; primary: boolean; size: number; hashes: { sha1: string } }[];
  dependencies: { project_id: string | null; version_id: string | null; dependency_type: 'required' | 'optional' | 'incompatible' | 'embedded' }[];
}

interface ModrinthProject {
  id: string;
  slug: string;
  title: string;
  icon_url: string | null;
  project_type: string;
}

/** Welche Modrinth-"Loader" zu einem Projekttyp passen. */
export function loadersFor(type: ProjectType, loader: Loader | null): string[] {
  if (type === 'resourcepack') return ['minecraft'];
  if (type === 'shader') return ['iris', 'optifine'];
  return loader ? [loader] : [];
}

const q = (v: unknown) => encodeURIComponent(JSON.stringify(v));

export async function search(params: SearchParams, loader: Loader | null): Promise<SearchResult> {
  const facets: string[][] = [[`project_type:${params.type}`], [`versions:${params.gameVersion}`]];
  if (params.type === 'mod' && loader) facets.push([`categories:${loader}`]);
  if (params.type === 'shader') facets.push(['categories:iris']);
  if (params.category) facets.push([`categories:${params.category}`]);
  const index = params.sort ?? (params.query ? 'relevance' : 'downloads');
  const url =
    `${API}/search?query=${encodeURIComponent(params.query)}&facets=${q(facets)}` +
    `&index=${index}&limit=20&offset=${params.offset}`;
  const res = await fetchJson<{
    hits: { project_id: string; slug: string; title: string; description: string; author: string; downloads: number; icon_url: string | null }[];
    total_hits: number;
  }>(url);
  return {
    total: res.total_hits,
    hits: res.hits.map((h) => ({
      projectId: h.project_id,
      slug: h.slug,
      title: h.title,
      description: h.description,
      author: h.author,
      downloads: h.downloads,
      iconUrl: h.icon_url || null,
    })),
  };
}

export async function getProject(idOrSlug: string): Promise<ModrinthProject> {
  return fetchJson<ModrinthProject>(`${API}/project/${idOrSlug}`);
}

/** Neueste Version eines Projekts für Spielversion + Loader (oder null, wenn es keine gibt). */
export async function latestVersion(idOrSlug: string, gameVersion: string, loaders: string[]): Promise<ModrinthVersion | null> {
  try {
    const url = `${API}/project/${idOrSlug}/version?game_versions=${q([gameVersion])}${loaders.length ? `&loaders=${q(loaders)}` : ''}`;
    const versions = await fetchJson<ModrinthVersion[]>(url);
    return versions[0] ?? null;
  } catch (e) {
    if (e instanceof HttpError && e.status === 404) return null;
    throw e;
  }
}

/** Modrinth-Projekt von Iris – Shader laufen nur damit. */
export const IRIS_PROJECT_ID = 'YL57xq9U';
const shaderSupport = new Map<string, Promise<boolean>>();

/** Gibt es Iris für diese Spielversion? (Iris gibt es erst ab 1.16.5.) Ohne Antwort von Modrinth: ja annehmen. */
export function shadersSupported(gameVersion: string, loader: Loader | null): Promise<boolean> {
  let result = shaderSupport.get(gameVersion);
  if (!result) {
    result = latestVersion(IRIS_PROJECT_ID, gameVersion, loadersFor('mod', loader)).then(
      (v) => v !== null,
      () => {
        shaderSupport.delete(gameVersion);
        return true;
      },
    );
    shaderSupport.set(gameVersion, result);
  }
  return result;
}

export async function getVersion(id: string): Promise<ModrinthVersion> {
  return fetchJson<ModrinthVersion>(`${API}/version/${id}`);
}

export function primaryFile(v: ModrinthVersion) {
  return v.files.find((f) => f.primary) ?? v.files[0];
}

/**
 * Löst ein Projekt samt aller Pflicht-Abhängigkeiten auf (z. B. braucht Mod Menu die Fabric API).
 * Gibt die Liste aller zu installierenden Versionen zurück.
 */
export async function resolveWithDependencies(
  projectId: string,
  gameVersion: string,
  loaders: string[],
): Promise<ModrinthVersion[]> {
  const result = new Map<string, ModrinthVersion>();
  const queue: { projectId: string | null; versionId: string | null }[] = [{ projectId, versionId: null }];

  while (queue.length > 0) {
    const next = queue.shift()!;
    if (next.projectId && result.has(next.projectId)) continue;
    const version = next.versionId
      ? await getVersion(next.versionId)
      : await latestVersion(next.projectId!, gameVersion, loaders);
    if (!version) {
      if (next.projectId === projectId) throw new LocalizedError('error.noCompatibleFile');
      continue; // optionale Lücke in Abhängigkeiten – überspringen statt abbrechen
    }
    if (result.has(version.project_id)) continue;
    result.set(version.project_id, version);
    for (const dep of version.dependencies) {
      if (dep.dependency_type === 'required') queue.push({ projectId: dep.project_id, versionId: dep.version_id });
    }
  }
  return [...result.values()];
}
