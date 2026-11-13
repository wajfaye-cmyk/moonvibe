// 3D-Vorschau des Spielers mit Cosmetics (Startseite + Cosmetics-Seite) und Vorschaubilder für Karten.
import * as THREE from 'three';
import type { CosmeticSlot, SkinInfo } from '../shared/types';
import { animate, buildCape, buildModel, buildPlayer, normalizeSkin, pixelTexture, type AnimatedPart, type ModelJson, type PlayerParts } from './mcmodel.js';

export const SLOTS: CosmeticSlot[] = ['cape', 'hat', 'face', 'back', 'wings', 'shoes', 'pet'];
/** Diese Cosmetics sieht man besser von hinten. */
export const BACK_SLOTS: CosmeticSlot[] = ['cape', 'back', 'wings'];

const loader = new THREE.TextureLoader();
loader.setCrossOrigin('anonymous');

export async function loadTexture(url: string): Promise<THREE.Texture> {
  return pixelTexture(await loader.loadAsync(url));
}

/** Skin laden (alte 64×32-Skins werden umgewandelt), sonst ein schlichter Platzhalter. */
async function skinTexture(skin: SkinInfo): Promise<THREE.Texture> {
  if (skin.dataUrl) {
    const image = new Image();
    image.src = skin.dataUrl;
    await image.decode();
    return pixelTexture(new THREE.CanvasTexture(normalizeSkin(image)));
  }
  const canvas = document.createElement('canvas');
  canvas.width = 64;
  canvas.height = 64;
  const ctx = canvas.getContext('2d')!;
  ctx.fillStyle = '#c99c7a';
  ctx.fillRect(0, 0, 64, 64);
  ctx.fillStyle = '#2b4a7a';
  ctx.fillRect(16, 16, 40, 16);
  ctx.fillRect(32, 48, 16, 16);
  ctx.fillStyle = '#1d2b45';
  ctx.fillRect(0, 16, 16, 16);
  ctx.fillRect(16, 48, 16, 16);
  return pixelTexture(new THREE.CanvasTexture(canvas));
}

function lights(scene: THREE.Scene): void {
  scene.add(new THREE.AmbientLight(0xffffff, 1.9));
  const key = new THREE.DirectionalLight(0xffffff, 1.6);
  key.position.set(-30, 50, -60);
  scene.add(key);
  const rim = new THREE.DirectionalLight(0x8fcaff, 1.1);
  rim.position.set(40, 30, 60);
  scene.add(rim);
  const warm = new THREE.DirectionalLight(0xffc56b, 0.5);
  warm.position.set(60, -10, -20);
  scene.add(warm);
}

interface Equipped {
  group: THREE.Object3D;
  animated: AnimatedPart[];
  /** second copy for the left leg (shoes) */
  extra?: THREE.Object3D;
}

export interface PreviewOptions {
  /** Kamera-Abstand (größer = Figur kleiner) */
  distance?: number;
  /** Blickhöhe auf die Figur */
  lookAt?: number;
  /** Start-Drehung */
  yaw?: number;
  autoRotate?: boolean;
}

export class PlayerPreview {
  private readonly renderer: THREE.WebGLRenderer;
  private readonly scene = new THREE.Scene();
  private readonly camera = new THREE.PerspectiveCamera(30, 1, 1, 400);
  private readonly turntable = new THREE.Group();
  private player: PlayerParts | null = null;
  private readonly slots: Partial<Record<CosmeticSlot, Equipped>> = {};
  private yaw: number;
  private targetYaw: number;
  private dragging = false;
  private lastInteraction = 0;
  private readonly started = performance.now();
  private visible = true;

  constructor(
    private readonly canvas: HTMLCanvasElement,
    private readonly options: PreviewOptions = {},
  ) {
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
    this.renderer.setPixelRatio(window.devicePixelRatio);
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    lights(this.scene);
    this.scene.add(this.turntable);
    this.turntable.add(this.shadow());
    const distance = options.distance ?? 78;
    const lookAt = options.lookAt ?? 16;
    this.camera.position.set(0, lookAt + 4, -distance);
    this.camera.lookAt(0, lookAt, 0);
    this.yaw = this.targetYaw = options.yaw ?? -0.5;

    canvas.addEventListener('pointerdown', (e) => {
      this.dragging = true;
      canvas.setPointerCapture(e.pointerId);
    });
    canvas.addEventListener('pointermove', (e) => {
      if (!this.dragging) return;
      this.targetYaw += e.movementX * 0.012;
      this.lastInteraction = performance.now();
    });
    const stop = () => {
      this.dragging = false;
      this.lastInteraction = performance.now();
    };
    canvas.addEventListener('pointerup', stop);
    canvas.addEventListener('pointercancel', stop);
    new ResizeObserver(() => this.resize()).observe(canvas);
    // Nur rendern, wenn sichtbar (spart Grafikleistung)
    new IntersectionObserver((entries) => (this.visible = entries.some((e) => e.isIntersecting))).observe(canvas);
    this.resize();
    this.renderer.setAnimationLoop(() => this.frame());
  }

  private shadow(): THREE.Mesh {
    const c = document.createElement('canvas');
    c.width = 128;
    c.height = 128;
    const ctx = c.getContext('2d')!;
    const g = ctx.createRadialGradient(64, 64, 4, 64, 64, 64);
    g.addColorStop(0, 'rgba(0,0,0,0.6)');
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, 128, 128);
    const mesh = new THREE.Mesh(new THREE.PlaneGeometry(26, 26), new THREE.MeshBasicMaterial({ map: new THREE.CanvasTexture(c), transparent: true, depthWrite: false }));
    mesh.rotation.x = -Math.PI / 2;
    mesh.position.y = 0.05;
    return mesh;
  }

  private resize(): void {
    const { clientWidth: w, clientHeight: h } = this.canvas;
    if (!w || !h) return;
    this.renderer.setSize(w, h, false);
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
  }

  /** Dreht die Figur weich zur Vorder- oder Rückseite. */
  face(back: boolean): void {
    const base = back ? Math.PI - 0.5 : -0.5;
    const turns = Math.round((this.targetYaw - base) / (Math.PI * 2));
    this.targetYaw = base + turns * Math.PI * 2;
    this.lastInteraction = performance.now();
  }

  async setSkin(skin: SkinInfo): Promise<void> {
    const texture = await skinTexture(skin);
    if (this.player) this.turntable.remove(this.player.root);
    this.player = buildPlayer(texture, skin.slim);
    for (const slot of SLOTS) {
      const item = this.slots[slot];
      if (item) this.attach(slot, item);
    }
    this.turntable.add(this.player.root);
  }

  private attach(slot: CosmeticSlot, item: Equipped): void {
    if (!this.player) return;
    if (slot === 'shoes') {
      this.player.rightLeg.add(item.group);
      if (item.extra) this.player.leftLeg.add(item.extra);
      return;
    }
    (slot === 'hat' || slot === 'face' ? this.player.head : this.player.body).add(item.group);
  }

  private clear(slot: CosmeticSlot): void {
    const item = this.slots[slot];
    item?.group.parent?.remove(item.group);
    item?.extra?.parent?.remove(item.extra);
    delete this.slots[slot];
  }

  async setCape(textureUrl: string | null): Promise<void> {
    if (!textureUrl) return this.clear('cape');
    const texture = await loadTexture(textureUrl);
    this.clear('cape');
    const group = buildCape(texture);
    this.slots.cape = { group, animated: [] };
    this.attach('cape', this.slots.cape);
  }

  async setModel(slot: Exclude<CosmeticSlot, 'cape'>, model: ModelJson | null, textureUrl: string | null): Promise<void> {
    if (!model || !textureUrl) return this.clear(slot);
    const texture = await loadTexture(textureUrl);
    this.clear(slot);
    const built: Equipped = buildModel(model, texture);
    if (model.attach === 'legs') {
      // one shoe per leg
      const second = buildModel(model, texture);
      built.extra = second.group;
      built.animated = [...built.animated, ...second.animated];
    }
    this.slots[slot] = built;
    this.attach(slot, built);
  }

  private frame(): void {
    if (!this.visible) return;
    const seconds = (performance.now() - this.started) / 1000;
    const idle = performance.now() - this.lastInteraction > 4000;
    if (!this.dragging && idle && this.options.autoRotate !== false) this.targetYaw += 0.004;
    this.yaw += (this.targetYaw - this.yaw) * 0.12;
    this.turntable.rotation.y = this.yaw;

    if (this.player) {
      const breathe = Math.sin(seconds * 1.6);
      this.player.rightArm.rotation.z = 0.06 + breathe * 0.04;
      this.player.leftArm.rotation.z = -0.06 - breathe * 0.04;
      this.player.head.rotation.x = Math.sin(seconds * 0.7) * 0.05;
    }
    for (const item of Object.values(this.slots)) if (item) animate(item.animated, seconds);
    this.renderer.render(this.scene, this.camera);
  }
}

// ---------- Vorschaubilder ----------

let thumbRenderer: THREE.WebGLRenderer | null = null;

function thumbnailRenderer(size: number): THREE.WebGLRenderer {
  if (!thumbRenderer) {
    thumbRenderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
    thumbRenderer.outputColorSpace = THREE.SRGBColorSpace;
  }
  thumbRenderer.setSize(size, size, false);
  return thumbRenderer;
}

/** Rendert ein einzelnes Cosmetic als Bild (data:-URL). */
export async function renderThumbnail(slot: CosmeticSlot, textureUrl: string, model: ModelJson | null, size = 256): Promise<string> {
  const scene = new THREE.Scene();
  lights(scene);
  const texture = await loadTexture(textureUrl);
  const root = new THREE.Group();
  root.scale.set(-1, -1, 1); // wie beim Spieler: Minecraft-Modellraum
  if (slot === 'cape') root.add(buildCape(texture));
  else if (model) {
    const built = buildModel(model, texture);
    animate(built.animated, 0.3);
    root.add(built.group);
  }
  const holder = new THREE.Group();
  holder.add(root);
  holder.rotation.y = BACK_SLOTS.includes(slot) ? Math.PI - 0.45 : -0.55;
  scene.add(holder);

  const box = new THREE.Box3().setFromObject(holder);
  const center = box.getCenter(new THREE.Vector3());
  const radius = box.getSize(new THREE.Vector3()).length() / 2;
  const camera = new THREE.PerspectiveCamera(28, 1, 0.1, 500);
  const distance = radius / Math.sin((28 * Math.PI) / 360) * 1.05;
  camera.position.set(center.x, center.y + distance * 0.22, center.z - distance);
  camera.lookAt(center);

  const renderer = thumbnailRenderer(size);
  renderer.setClearColor(0x000000, 0);
  renderer.render(scene, camera);
  const url = renderer.domElement.toDataURL('image/png');
  texture.dispose();
  return url;
}
