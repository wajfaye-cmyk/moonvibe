// Minecraft-Modelle in three.js – exakt wie im Spiel (gleiche Würfel- und Textur-Anordnung wie ModelPart.Cube).
// Koordinaten sind Minecraft-Pixel im Modellraum: y zeigt nach unten, -z ist vorne (Gesicht).
import * as THREE from 'three';

type Vec3 = [number, number, number];

export interface CubeJson {
  from: Vec3;
  size: Vec3;
  uv: [number, number];
  inflate?: number;
  mirror?: boolean;
}

export interface AnimationJson {
  type: 'flap' | 'spin' | 'bob' | 'sway';
  axis?: 'x' | 'y' | 'z';
  amplitude?: number;
  speed?: number;
  phase?: number;
}

export interface PartJson {
  name?: string;
  pivot?: Vec3;
  rotation?: Vec3;
  cubes?: CubeJson[];
  animation?: AnimationJson;
  emissive?: boolean;
  children?: PartJson[];
}

export interface ModelJson {
  attach: 'head' | 'body' | 'legs';
  textureWidth: number;
  textureHeight: number;
  parts: PartJson[];
}

const DEG = Math.PI / 180;

/** Würfel-Geometrie mit Minecrafts Box-UV (Reihenfolge und Ausrichtung wie im Spiel). */
export function mcCube(cube: CubeJson, texW: number, texH: number): THREE.BufferGeometry {
  const [x0, y0, z0] = cube.from;
  const [w, h, d] = cube.size;
  const g = cube.inflate ?? 0;
  let minX = x0 - g;
  let maxX = x0 + w + g;
  const minY = y0 - g;
  const maxY = y0 + h + g;
  const minZ = z0 - g;
  const maxZ = z0 + d + g;
  if (cube.mirror) [minX, maxX] = [maxX, minX];

  const t0: Vec3 = [minX, minY, minZ];
  const t1: Vec3 = [maxX, minY, minZ];
  const t2: Vec3 = [maxX, maxY, minZ];
  const t3: Vec3 = [minX, maxY, minZ];
  const l0: Vec3 = [minX, minY, maxZ];
  const l1: Vec3 = [maxX, minY, maxZ];
  const l2: Vec3 = [maxX, maxY, maxZ];
  const l3: Vec3 = [minX, maxY, maxZ];

  const [u, v] = cube.uv;
  const u0 = u;
  const u1 = u + d;
  const u2 = u + d + w;
  const u22 = u + d + w + w;
  const u3 = u + d + w + d;
  const u4 = u + d + w + d + w;
  const v0 = v;
  const v1 = v + d;
  const v2 = v + d + h;

  // [Ecken, uA, vA, uB, vB] – wie ModelPart.Polygon: Ecke0→(uB,vA) Ecke1→(uA,vA) Ecke2→(uA,vB) Ecke3→(uB,vB)
  const faces: [Vec3[], number, number, number, number][] = [
    [[l1, l0, t0, t1], u1, v0, u2, v1], // DOWN (im Modellraum oben)
    [[t2, t3, l3, l2], u2, v1, u22, v0], // UP
    [[t0, l0, l3, t3], u0, v1, u1, v2], // WEST
    [[t1, t0, t3, t2], u1, v1, u2, v2], // NORTH (vorne)
    [[l1, t1, t2, l2], u2, v1, u3, v2], // EAST
    [[l0, l1, l2, l3], u3, v1, u4, v2], // SOUTH (hinten)
  ];

  const positions: number[] = [];
  const uvs: number[] = [];
  const indices: number[] = [];
  for (const [verts, ua, va, ub, vb] of faces) {
    const corner: [number, number][] = [
      [ub, va],
      [ua, va],
      [ua, vb],
      [ub, vb],
    ];
    const order = cube.mirror ? [3, 2, 1, 0] : [0, 1, 2, 3];
    const base = positions.length / 3;
    for (const i of order) {
      positions.push(...verts[i]);
      uvs.push(corner[i][0] / texW, 1 - corner[i][1] / texH);
    }
    indices.push(base, base + 1, base + 2, base, base + 2, base + 3);
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  geometry.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2));
  geometry.setIndex(indices);
  geometry.computeVertexNormals();
  return geometry;
}

export function pixelTexture(texture: THREE.Texture): THREE.Texture {
  texture.magFilter = THREE.NearestFilter;
  texture.minFilter = THREE.NearestFilter;
  texture.generateMipmaps = false;
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

export function material(texture: THREE.Texture, emissive = false): THREE.Material {
  const options = { map: texture, alphaTest: 0.1, side: THREE.DoubleSide };
  return emissive ? new THREE.MeshBasicMaterial(options) : new THREE.MeshLambertMaterial(options);
}

/** Teil mit Animation – wird jedes Bild aktualisiert. */
export interface AnimatedPart {
  object: THREE.Object3D;
  animation: AnimationJson;
  baseRotation: THREE.Euler;
  baseY: number;
}

export interface BuiltModel {
  group: THREE.Group;
  animated: AnimatedPart[];
}

function setPose(object: THREE.Object3D, pivot: Vec3 = [0, 0, 0], rotation: Vec3 = [0, 0, 0]): void {
  object.position.set(pivot[0], pivot[1], pivot[2]);
  // Minecraft dreht Z, dann Y, dann X (rotationZYX) → three.js-Reihenfolge "ZYX"
  object.rotation.set(rotation[0] * DEG, rotation[1] * DEG, rotation[2] * DEG, 'ZYX');
}

/** Baut ein Cosmetic-Modell aus der Server-JSON. */
export function buildModel(json: ModelJson, texture: THREE.Texture): BuiltModel {
  const group = new THREE.Group();
  const animated: AnimatedPart[] = [];
  const normal = material(texture);
  const glow = material(texture, true);

  const addPart = (parent: THREE.Object3D, part: PartJson) => {
    const node = new THREE.Group();
    setPose(node, part.pivot, part.rotation);
    for (const cube of part.cubes ?? []) {
      node.add(new THREE.Mesh(mcCube(cube, json.textureWidth, json.textureHeight), part.emissive ? glow : normal));
    }
    if (part.animation) animated.push({ object: node, animation: part.animation, baseRotation: node.rotation.clone(), baseY: node.position.y });
    parent.add(node);
    for (const child of part.children ?? []) addPart(node, child);
  };
  for (const part of json.parts) addPart(group, part);
  return { group, animated };
}

/** Animationen wie im Spiel (CosmeticModel.setupAnim). */
export function animate(parts: AnimatedPart[], seconds: number, movement = 0): void {
  for (const p of parts) {
    const a = p.animation;
    const amplitude = a.amplitude ?? 10;
    const speed = a.speed ?? 1;
    const t = seconds * speed * Math.PI * 2 + (a.phase ?? 0) * DEG;
    p.object.rotation.copy(p.baseRotation);
    p.object.position.y = p.baseY;
    const axis = a.axis ?? 'y';
    switch (a.type) {
      case 'flap':
        p.object.rotation[axis] += Math.sin(t * (1 + movement)) * amplitude * (1 + movement * 0.6) * DEG;
        break;
      case 'spin':
        p.object.rotation[axis] += seconds * speed * Math.PI * 2;
        break;
      case 'bob':
        p.object.position.y += Math.sin(t) * amplitude;
        break;
      case 'sway':
        p.object.rotation[axis] += Math.sin(t) * amplitude * DEG;
        break;
    }
  }
}

// ---------- Spieler ----------

export interface PlayerParts {
  root: THREE.Group;
  head: THREE.Group;
  body: THREE.Group;
  rightArm: THREE.Group;
  leftArm: THREE.Group;
  rightLeg: THREE.Group;
  leftLeg: THREE.Group;
}

/** Spielermodell (64×64-Skin), optional mit schmalen Armen ("Alex"). */
export function buildPlayer(skin: THREE.Texture, slim: boolean): PlayerParts {
  const base = material(skin);
  const overlay = material(skin);
  const part = (pivot: Vec3, cubes: [CubeJson, boolean][]) => {
    const g = new THREE.Group();
    setPose(g, pivot);
    for (const [cube, isOverlay] of cubes) g.add(new THREE.Mesh(mcCube(cube, 64, 64), isOverlay ? overlay : base));
    return g;
  };
  const armW = slim ? 3 : 4;
  const head = part([0, 0, 0], [
    [{ from: [-4, -8, -4], size: [8, 8, 8], uv: [0, 0] }, false],
    [{ from: [-4, -8, -4], size: [8, 8, 8], uv: [32, 0], inflate: 0.5 }, true],
  ]);
  const body = part([0, 0, 0], [
    [{ from: [-4, 0, -2], size: [8, 12, 4], uv: [16, 16] }, false],
    [{ from: [-4, 0, -2], size: [8, 12, 4], uv: [16, 32], inflate: 0.25 }, true],
  ]);
  const rightArm = part([-5, slim ? 2.5 : 2, 0], [
    [{ from: [slim ? -2 : -3, -2, -2], size: [armW, 12, 4], uv: [40, 16] }, false],
    [{ from: [slim ? -2 : -3, -2, -2], size: [armW, 12, 4], uv: [40, 32], inflate: 0.25 }, true],
  ]);
  const leftArm = part([5, slim ? 2.5 : 2, 0], [
    [{ from: [-1, -2, -2], size: [armW, 12, 4], uv: [32, 48] }, false],
    [{ from: [-1, -2, -2], size: [armW, 12, 4], uv: [48, 48], inflate: 0.25 }, true],
  ]);
  const rightLeg = part([-1.9, 12, 0], [
    [{ from: [-2, 0, -2], size: [4, 12, 4], uv: [0, 16] }, false],
    [{ from: [-2, 0, -2], size: [4, 12, 4], uv: [0, 32], inflate: 0.25 }, true],
  ]);
  const leftLeg = part([1.9, 12, 0], [
    [{ from: [-2, 0, -2], size: [4, 12, 4], uv: [16, 48] }, false],
    [{ from: [-2, 0, -2], size: [4, 12, 4], uv: [0, 48], inflate: 0.25 }, true],
  ]);

  // Minecraft zeichnet Entities mit scale(-1, -1, 1); Füße landen so bei y = 0.
  const root = new THREE.Group();
  root.scale.set(-1, -1, 1);
  root.position.y = 24;
  root.add(head, body, rightArm, leftArm, rightLeg, leftLeg);
  return { root, head, body, rightArm, leftArm, rightLeg, leftLeg };
}

/** Cape wie im Spiel: hinter dem Körper, leicht nach hinten geneigt. */
export function buildCape(texture: THREE.Texture): THREE.Group {
  const holder = new THREE.Group();
  holder.position.set(0, 0, 2);
  // setupAnim des Capes ergibt zusammen mit der Grundpose: zuerst 180° um Y, dann 6° um X
  holder.quaternion.setFromEuler(new THREE.Euler(6 * DEG, Math.PI, 0, 'XYZ'));
  holder.add(new THREE.Mesh(mcCube({ from: [-5, 0, -1], size: [10, 16, 1], uv: [0, 0] }, 64, 32), material(texture)));
  return holder;
}

/** Alte 64×32-Skins in das heutige 64×64-Format umwandeln (linkes Bein/Arm = gespiegelt rechts). */
export function normalizeSkin(image: HTMLImageElement): HTMLCanvasElement {
  const canvas = document.createElement('canvas');
  canvas.width = 64;
  canvas.height = 64;
  const ctx = canvas.getContext('2d')!;
  ctx.drawImage(image, 0, 0);
  if (image.height === 32) {
    ctx.drawImage(canvas, 0, 16, 16, 16, 16, 48, 16, 16);
    ctx.drawImage(canvas, 40, 16, 16, 16, 32, 48, 16, 16);
  }
  return canvas;
}
