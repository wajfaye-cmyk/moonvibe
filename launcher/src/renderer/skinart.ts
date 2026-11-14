// Prepares an uploaded skin: brings old 64×32 skins to 64×64, detects slim arms and creates a version
// for each arm model (classic = 4 px wide arms, slim = 3 px). The game decides the arm model of players
// without a Mojang skin by itself, so the launcher needs a fitting image for both.
import type { SkinUpload } from '../shared/types';

function canvas(width: number, height: number): [HTMLCanvasElement, CanvasRenderingContext2D] {
  const c = document.createElement('canvas');
  c.width = width;
  c.height = height;
  const ctx = c.getContext('2d', { willReadFrequently: true })!;
  ctx.imageSmoothingEnabled = false;
  return [c, ctx];
}

/** Copies a rectangle mirrored left↔right (old skins have only one arm and one leg). */
function copyFlipped(ctx: CanvasRenderingContext2D, src: CanvasImageSource, sx: number, sy: number, w: number, h: number, dx: number, dy: number): void {
  ctx.save();
  ctx.translate(dx + w, dy);
  ctx.scale(-1, 1);
  ctx.drawImage(src, sx, sy, w, h, 0, 0, w, h);
  ctx.restore();
}

/** 64×64 canvas from a 64×64 or (old) 64×32 skin. */
function normalize(image: HTMLImageElement): HTMLCanvasElement {
  const [c, ctx] = canvas(64, 64);
  ctx.drawImage(image, 0, 0);
  if (image.naturalHeight === 32) {
    const [copy, copyCtx] = canvas(64, 32);
    copyCtx.drawImage(image, 0, 0);
    // left leg from the right leg
    copyFlipped(ctx, copy, 4, 16, 4, 4, 20, 48);
    copyFlipped(ctx, copy, 8, 16, 4, 4, 24, 48);
    copyFlipped(ctx, copy, 0, 20, 4, 12, 24, 52);
    copyFlipped(ctx, copy, 4, 20, 4, 12, 20, 52);
    copyFlipped(ctx, copy, 8, 20, 4, 12, 16, 52);
    copyFlipped(ctx, copy, 12, 20, 4, 12, 28, 52);
    // left arm from the right arm
    copyFlipped(ctx, copy, 44, 16, 4, 4, 36, 48);
    copyFlipped(ctx, copy, 48, 16, 4, 4, 40, 48);
    copyFlipped(ctx, copy, 40, 20, 4, 12, 40, 52);
    copyFlipped(ctx, copy, 44, 20, 4, 12, 36, 52);
    copyFlipped(ctx, copy, 48, 20, 4, 12, 32, 52);
    copyFlipped(ctx, copy, 52, 20, 4, 12, 44, 52);
  }
  return c;
}

/** Slim skins leave the last column of the arm area empty. */
function looksSlim(skin: HTMLCanvasElement): boolean {
  const data = skin.getContext('2d', { willReadFrequently: true })!.getImageData(54, 20, 2, 12).data;
  for (let i = 3; i < data.length; i += 4) if (data[i] !== 0) return false;
  return true;
}

/** Top-left corners of the four arm areas (16×16 each): right arm, right sleeve, left arm, left sleeve. */
const ARMS: [number, number][] = [
  [40, 16],
  [40, 32],
  [32, 48],
  [48, 48],
];

/** The six faces of an arm with the given width (3 or 4): [x, y, width, height] relative to the arm area. */
function armFaces(w: number): [number, number, number, number][] {
  return [
    [4, 0, w, 4], // top
    [4 + w, 0, w, 4], // bottom
    [0, 4, 4, 12], // outer side
    [4, 4, w, 12], // front
    [4 + w, 4, 4, 12], // inner side
    [8 + w, 4, w, 12], // back
  ];
}

/** Redraws the arms of a skin from one arm width to the other. */
function convertArms(skin: HTMLCanvasElement, from: 3 | 4, to: 3 | 4): HTMLCanvasElement {
  const [c, ctx] = canvas(64, 64);
  ctx.drawImage(skin, 0, 0);
  const source = armFaces(from);
  const target = armFaces(to);
  for (const [ox, oy] of ARMS) {
    ctx.clearRect(ox, oy, 16, 16);
    source.forEach(([sx, sy, sw, sh], i) => {
      const [dx, dy, dw, dh] = target[i]!;
      ctx.drawImage(skin, ox + sx, oy + sy, sw, sh, ox + dx, oy + dy, dw, dh);
    });
  }
  return c;
}

async function png(c: HTMLCanvasElement): Promise<Uint8Array> {
  const blob = await new Promise<Blob | null>((resolve) => c.toBlob(resolve, 'image/png'));
  if (!blob) throw new Error('PNG');
  return new Uint8Array(await blob.arrayBuffer());
}

/** Face (with the hat layer on top) as a small picture. */
function face(skin: HTMLCanvasElement): string {
  const [c, ctx] = canvas(64, 64);
  ctx.drawImage(skin, 8, 8, 8, 8, 0, 0, 64, 64);
  ctx.drawImage(skin, 40, 8, 8, 8, 0, 0, 64, 64);
  return c.toDataURL('image/png');
}

/** Is this image usable as a skin (64×64 or the old 64×32 format)? */
export function isSkinImage(image: HTMLImageElement): boolean {
  return image.naturalWidth === 64 && (image.naturalHeight === 64 || image.naturalHeight === 32);
}

/**
 * Builds everything the launcher stores for a skin.
 * @param slimModel arm model the skin was made for (null = detect)
 */
export async function prepareSkin(image: HTMLImageElement, slimModel: boolean | null): Promise<SkinUpload> {
  const skin = normalize(image);
  const slim = slimModel ?? (image.naturalHeight === 64 && looksSlim(skin));
  return {
    png: await png(skin),
    wide: await png(slim ? convertArms(skin, 3, 4) : skin),
    slim: await png(slim ? skin : convertArms(skin, 4, 3)),
    slimModel: slim,
    avatar: face(skin),
  };
}
