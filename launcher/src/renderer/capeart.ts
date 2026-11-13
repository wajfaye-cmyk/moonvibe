// Macht aus einem beliebigen Bild eine Cape-Textur (Minecraft-Aufteilung, 8-fache Auflösung).
// Fertige Cape-Dateien (64×32 bzw. OptiFine 46×22 und Vielfache) werden unverändert übernommen.

export type CapeFit = 'fill' | 'contain' | 'stretch';
export type CapeInside = 'image' | 'color';

export interface CapeOptions {
  fit: CapeFit;
  /** Hintergrund/Ränder; null = automatisch aus dem Bild */
  background: string | null;
  inside: CapeInside;
  /** Bild als fertige Cape-Datei verwenden */
  template: boolean;
}

/** Pixel je Cape-Pixel (64×32 → 512×256) */
const SCALE = 8;
const W = 64 * SCALE;
const H = 32 * SCALE;

/** Bereiche im 64×32-Raster: Außenseite, Innenseite, Ränder, Elytra */
const FRONT = [1, 1, 10, 16];
const BACK = [12, 1, 10, 16];
const EDGES = [
  [1, 0, 10, 1], // oben
  [11, 0, 10, 1], // unten
  [0, 1, 1, 16], // links
  [11, 1, 1, 16], // rechts
];
const ELYTRA = [22, 0, 24, 22];
const ELYTRA_WINGS = [
  [24, 2, 10, 20],
  [36, 2, 10, 20],
];

/** Sieht das Bild wie eine fertige Cape-Datei aus (Format + leere Ecke oben links)? */
export function looksLikeTemplate(image: HTMLImageElement): boolean {
  if (!templateWidth(image)) return false;
  const canvas = document.createElement('canvas');
  canvas.width = image.naturalWidth;
  canvas.height = image.naturalHeight;
  const ctx = canvas.getContext('2d', { willReadFrequently: true })!;
  ctx.drawImage(image, 0, 0);
  const unit = image.naturalHeight / (image.naturalWidth / image.naturalHeight > 2.05 ? 22 : 32);
  const corner = ctx.getImageData(0, 0, Math.max(1, Math.floor(unit)), Math.max(1, Math.floor(unit))).data;
  for (let i = 3; i < corner.length; i += 4) if (corner[i] > 10) return false;
  return true;
}

/** Breite des Bildes in 64er-Cape-Einheiten (null = kein Cape-Format) */
function templateWidth(image: HTMLImageElement): number | null {
  const ratio = image.naturalWidth / image.naturalHeight;
  if (Math.abs(ratio - 2) < 0.01) return 64;
  if (Math.abs(ratio - 46 / 22) < 0.01) return 46; // OptiFine-Format
  return null;
}

export function canUseAsTemplate(image: HTMLImageElement): boolean {
  return templateWidth(image) !== null;
}

function sample(image: HTMLImageElement): Uint8ClampedArray {
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 32;
  const ctx = canvas.getContext('2d', { willReadFrequently: true })!;
  ctx.drawImage(image, 0, 0, 32, 32);
  return ctx.getImageData(0, 0, 32, 32).data;
}

/** Logo mit durchsichtigem Hintergrund? Dann besser einpassen statt zuschneiden. */
export function hasTransparency(image: HTMLImageElement): boolean {
  const data = sample(image);
  let clear = 0;
  for (let i = 3; i < data.length; i += 4) if (data[i] < 32) clear++;
  return clear / (data.length / 4) > 0.08;
}

/** Durchschnittsfarbe (ohne durchsichtige Pixel), abgedunkelt – bei Logos kräftig, damit es sich abhebt. */
export function averageColor(image: HTMLImageElement): string {
  const data = sample(image);
  let r = 0;
  let g = 0;
  let b = 0;
  let n = 0;
  for (let i = 0; i < data.length; i += 4) {
    if (data[i + 3] < 128) continue;
    r += data[i];
    g += data[i + 1];
    b += data[i + 2];
    n++;
  }
  if (!n) return '#1b1d24';
  const f = (n / (data.length / 4) < 0.92 ? 0.35 : 0.8) / n;
  const hex = (v: number) => Math.round(v * f).toString(16).padStart(2, '0');
  return `#${hex(r)}${hex(g)}${hex(b)}`;
}

function shadeColor(color: string, factor: number): string {
  const v = parseInt(color.slice(1), 16);
  const c = (shift: number) => Math.round(((v >> shift) & 255) * factor);
  return `rgb(${c(16)}, ${c(8)}, ${c(0)})`;
}

/** Bild in einen Bereich zeichnen (Füllen = zuschneiden, Einpassen = mit Rand, Strecken). */
function drawInto(ctx: CanvasRenderingContext2D, image: HTMLImageElement, [x, y, w, h]: number[], fit: CapeFit, mirror = false): void {
  const dx = x * SCALE;
  const dy = y * SCALE;
  const dw = w * SCALE;
  const dh = h * SCALE;
  const iw = image.naturalWidth;
  const ih = image.naturalHeight;
  // kleine Bilder (Pixel-Art) scharf vergrößern, große Bilder weich verkleinern
  ctx.imageSmoothingEnabled = iw > dw || ih > dh;
  ctx.imageSmoothingQuality = 'high';
  ctx.save();
  ctx.beginPath();
  ctx.rect(dx, dy, dw, dh);
  ctx.clip();
  if (mirror) {
    ctx.translate(dx * 2 + dw, 0);
    ctx.scale(-1, 1);
  }
  if (fit === 'stretch') {
    ctx.drawImage(image, dx, dy, dw, dh);
  } else {
    const scale = fit === 'fill' ? Math.max(dw / iw, dh / ih) : Math.min(dw / iw, dh / ih);
    const sw = iw * scale;
    const sh = ih * scale;
    ctx.drawImage(image, dx + (dw - sw) / 2, dy + (dh - sh) / 2, sw, sh);
  }
  ctx.restore();
}

function fill(ctx: CanvasRenderingContext2D, [x, y, w, h]: number[], color: string): void {
  ctx.fillStyle = color;
  ctx.fillRect(x * SCALE, y * SCALE, w * SCALE, h * SCALE);
}

/** Baut die fertige Cape-Textur (512×256). */
export function buildCapeTexture(image: HTMLImageElement, options: CapeOptions): HTMLCanvasElement {
  const canvas = document.createElement('canvas');
  canvas.width = W;
  canvas.height = H;
  const ctx = canvas.getContext('2d')!;

  const units = templateWidth(image);
  if (options.template && units) {
    // fertige Datei: auf 64×32-Raster skalieren (OptiFine-Dateien sind nur 46 Einheiten breit)
    const width = units * SCALE;
    ctx.imageSmoothingEnabled = image.naturalWidth > width;
    ctx.drawImage(image, 0, 0, width, (width * image.naturalHeight) / image.naturalWidth);
    return canvas;
  }

  const background = options.background ?? averageColor(image);
  const edge = shadeColor(background, 0.75);
  fill(ctx, FRONT, background);
  drawInto(ctx, image, FRONT, options.fit);
  if (options.inside === 'image') {
    fill(ctx, BACK, background);
    drawInto(ctx, image, BACK, options.fit, true);
    // Innenseite etwas dunkler, wie Stoff von innen
    ctx.fillStyle = 'rgba(0, 0, 0, 0.25)';
    ctx.fillRect(BACK[0] * SCALE, BACK[1] * SCALE, BACK[2] * SCALE, BACK[3] * SCALE);
  } else {
    fill(ctx, BACK, shadeColor(background, 0.9));
  }
  for (const area of EDGES) fill(ctx, area, edge);
  // Elytra bekommen dasselbe Motiv
  fill(ctx, ELYTRA, edge);
  for (const [i, wing] of ELYTRA_WINGS.entries()) {
    fill(ctx, wing, background);
    drawInto(ctx, image, wing, options.fit, i === 1);
  }
  return canvas;
}

export function canvasToPng(canvas: HTMLCanvasElement): Promise<Uint8Array> {
  return new Promise((resolve, reject) =>
    canvas.toBlob(async (blob) => (blob ? resolve(new Uint8Array(await blob.arrayBuffer())) : reject(new Error('PNG'))), 'image/png'),
  );
}
