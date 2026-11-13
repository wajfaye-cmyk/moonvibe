// Nur für Entwickler: rendert Vorschaubilder aller Cosmetics (wird von scripts/thumbnails.cjs geladen).
import type { CosmeticSlot } from '../shared/types';
import type { ModelJson } from './mcmodel.js';
import { renderThumbnail } from './preview3d.js';

interface Job {
  id: string;
  type: CosmeticSlot;
  texture: string;
  model: ModelJson | null;
}

(window as unknown as { renderAll: (jobs: Job[]) => Promise<Record<string, string>> }).renderAll = async (jobs) => {
  const result: Record<string, string> = {};
  for (const job of jobs) result[job.id] = await renderThumbnail(job.type, job.texture, job.model, 256);
  return result;
};
