import {
  AmbientLight,
  ArrowHelper,
  Box3,
  CanvasTexture,
  Color,
  ConeGeometry,
  CylinderGeometry,
  DirectionalLight,
  Group,
  Mesh,
  MeshBasicMaterial,
  MeshStandardMaterial,
  Object3D,
  PerspectiveCamera,
  Quaternion,
  Raycaster,
  Scene as ThreeScene,
  SphereGeometry,
  Sprite,
  SpriteMaterial,
  Vector2,
  Vector3,
  WebGLRenderer,
} from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import type { CameraPose, Connection3D, OptionValues, Site3D, Structure3D, Vector } from "../api/types";
import type { Highlights, MoleculeView, Pick, SelectionState, ViewListener } from "./MoleculeView";

type Style = "ball_and_stick" | "sticks" | "wireframe" | "spacefill";

interface Settings {
  style: Style;
  hydrogens: boolean;
  charges: boolean;
  axes: boolean;
  debugBonds: boolean;
  debugAngles: boolean;
  debugTorsions: boolean;
  debugClashes: boolean;
  debugStrain: boolean;
  debugLonePairs: boolean;
  debugThreshold: number;
}

interface Part {
  pick: Pick;
  object: Object3D;
  /** The molecule atom or bond index, or -1 if the drawing added it. */
  index: number;
}

const BALL_FRACTION = 0.25;
const LONE_ATOM_RADIUS = 0.2;
const AXIS_LENGTH = 2.5;
const FIELD_OF_VIEW = 30;
const HOVER = new Color("#ffd33d");
const END_COLOURS = [new Color("#ffd33d"), new Color("#79c0ff"), new Color("#ffa657")];
const SELECTED = new Color("#58a6ff");
const STRAIN = new Color("#e02020");
const STRAIN_SATURATES_AT = 6;
const UP = new Vector3(0, 1, 0);

const DEFAULTS: Settings = {
  style: "ball_and_stick",
  hydrogens: true,
  charges: true,
  axes: false,
  debugBonds: false,
  debugAngles: false,
  debugTorsions: false,
  debugClashes: false,
  debugStrain: false,
  debugLonePairs: false,
  debugThreshold: 1,
};

function settingsOf(options: OptionValues): Settings {
  const flag = (name: string, fallback: boolean) => (typeof options[name] === "boolean" ? (options[name] as boolean) : fallback);
  const style = options.style;
  return {
    style: typeof style === "string" ? (style as Style) : DEFAULTS.style,
    hydrogens: flag("hydrogens", DEFAULTS.hydrogens),
    charges: flag("charges", DEFAULTS.charges),
    axes: flag("axes", DEFAULTS.axes),
    debugBonds: flag("debug_bonds", false),
    debugAngles: flag("debug_angles", false),
    debugTorsions: flag("debug_torsions", false),
    debugClashes: flag("debug_clashes", false),
    debugStrain: flag("debug_strain", false),
    debugLonePairs: flag("debug_lone_pairs", false),
    debugThreshold: typeof options.debug_threshold === "number" ? options.debug_threshold : DEFAULTS.debugThreshold,
  };
}

export class ThreeView implements MoleculeView {
  readonly element = document.createElement("div");

  private readonly renderer = new WebGLRenderer({ antialias: true, alpha: true });
  private readonly scene = new ThreeScene();
  private readonly camera = new PerspectiveCamera(FIELD_OF_VIEW, 1, 0.1, 1000);
  private readonly controls: OrbitControls;
  private readonly raycaster = new Raycaster();
  private readonly resizer: ResizeObserver;
  private readonly unitCylinder = new CylinderGeometry(1, 1, 1, 12, 1, false);
  private readonly unitSphere = new SphereGeometry(1, 24, 16);
  private readonly unitCone = new ConeGeometry(1, 1, 12);

  private content = new Group();
  private settings: Settings;
  private parts: Part[] = [];
  private atomParts = new Map<number, Part>();
  private listeners = new Set<ViewListener>();
  private selection: SelectionState = { atoms: new Set(), bonds: new Set() };
  private highlights: Highlights = { hover: null, ends: [] };
  private frame = 0;
  private radius = 3;
  /** The middle of what is drawn, which the view starts out orbiting. */
  private centre = new Vector3();
  /** Every drawn atom, by site index, with the molecule atom it is or belongs to. */
  private sitePoints = new Map<number, { atom: number; at: Vector3 }>();
  private rightDown: { x: number; y: number } | null = null;
  private axes: Group | null = null;
  private readonly textures = new Map<string, CanvasTexture>();

  constructor(
    private readonly structure: Structure3D,
    options: OptionValues,
    private readonly conformer = 0,
  ) {
    this.settings = settingsOf(options);
    this.element.className = "view3d";

    const canvas = this.renderer.domElement;
    this.element.append(canvas);
    this.renderer.setPixelRatio(window.devicePixelRatio);

    this.scene.add(this.camera);
    this.scene.add(new AmbientLight(0xffffff, 0.75));
    const light = new DirectionalLight(0xffffff, 1.1);
    light.position.set(0.4, 0.8, 1);
    this.camera.add(light);

    this.controls = new OrbitControls(this.camera, canvas);
    this.controls.enableDamping = false;
    this.controls.addEventListener("change", () => {
      this.syncAxes();
      this.requestRender();
    });

    canvas.addEventListener("pointermove", this.onMove);
    canvas.addEventListener("pointerleave", this.onLeave);
    canvas.addEventListener("pointerdown", this.onDown);
    canvas.addEventListener("pointerup", this.onUp);

    this.resizer = new ResizeObserver(() => this.resize());
    this.resizer.observe(this.element);

    this.build();
    this.resetView();
  }

  setOptions(options: OptionValues): void {
    this.settings = settingsOf(options);
    this.build();
  }

  listen(listener: ViewListener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  setSelection(selection: SelectionState): void {
    this.selection = selection;
    this.refreshEmphasis();
  }

  setHighlights(highlights: Highlights): void {
    this.highlights = highlights;
    this.refreshEmphasis();
  }

  /** Where the camera is now, so the server can draw what it sees. */
  cameraPose(): CameraPose {
    const vector = ({ x, y, z }: Vector3): Vector => ({ x, y, z });
    return {
      eye: vector(this.camera.position),
      target: vector(this.controls.target),
      up: vector(this.camera.up),
      field_of_view: FIELD_OF_VIEW,
    };
  }

  /** The molecule atoms of everything drawn that is inside what the camera sees now. */
  visibleAtoms(): number[] {
    this.camera.updateMatrixWorld();
    const seen = new Set<number>();
    for (const { atom, at } of this.sitePoints.values()) {
      const ndc = at.clone().project(this.camera);
      if (Math.abs(ndc.x) <= 1 && Math.abs(ndc.y) <= 1 && Math.abs(ndc.z) <= 1) {
        seen.add(atom);
      }
    }
    return [...seen];
  }

  resetView(): void {
    const distance = (1.1 * this.radius) / Math.sin((FIELD_OF_VIEW * Math.PI) / 360);
    const yaw = (30 * Math.PI) / 180;
    const pitch = (20 * Math.PI) / 180;
    this.camera.position.set(
      Math.cos(pitch) * Math.sin(yaw) * distance,
      Math.sin(pitch) * distance,
      Math.cos(pitch) * Math.cos(yaw) * distance,
    ).add(this.centre);
    this.controls.target.copy(this.centre);
    this.syncAxes();
    this.camera.up.copy(UP);
    this.controls.update();
    this.requestRender();
  }

  dispose(): void {
    cancelAnimationFrame(this.frame);
    this.resizer.disconnect();
    const canvas = this.renderer.domElement;
    canvas.removeEventListener("pointermove", this.onMove);
    canvas.removeEventListener("pointerleave", this.onLeave);
    canvas.removeEventListener("pointerdown", this.onDown);
    canvas.removeEventListener("pointerup", this.onUp);
    this.controls.dispose();
    this.clearContent();
    this.textures.forEach((texture) => texture.dispose());
    this.unitCylinder.dispose();
    this.unitSphere.dispose();
    this.unitCone.dispose();
    this.renderer.dispose();
    this.listeners.clear();
  }

  private clearContent(): void {
    this.content.traverse((object) => {
      if (object instanceof Mesh || object instanceof Sprite) {
        const material = object.material;
        (Array.isArray(material) ? material : [material]).forEach((each) => each.dispose());
      }
    });
    this.scene.remove(this.content);
  }

  private build(): void {
    this.clearContent();
    this.content = new Group();
    this.parts = [];
    this.atomParts = new Map();

    const { sites, connections } = this.structure;
    const positions = this.structure.conformers[this.conformer]?.positions ?? [];
    const point = (index: number) => new Vector3(...(positions[index] ?? [0, 0, 0]));
    const diagnostics = this.structure.conformers[this.conformer]?.diagnostics;

    const anyHeavy = sites.some((site) => site.element !== "H");
    const shown = sites.map((site) => this.settings.hydrogens || !anyHeavy || site.element !== "H");
    const box = new Box3();
    sites.forEach((site) => shown[site.index] && box.expandByPoint(point(site.index)));
    this.centre = box.isEmpty() ? new Vector3() : box.getCenter(new Vector3());
    this.sitePoints = new Map(
      sites.filter((site) => shown[site.index]).map((site) => [site.index, { atom: site.atom, at: point(site.index) }]),
    );
    const links = connections.filter((link) => shown[link.first] && shown[link.second]);
    const bonded = new Set(links.flatMap((link) => [link.first, link.second]));
    const neighbours = new Map<number, number[]>();
    for (const link of links) {
      neighbours.set(link.first, [...(neighbours.get(link.first) ?? []), link.second]);
      neighbours.set(link.second, [...(neighbours.get(link.second) ?? []), link.first]);
    }

    let radius = 1;
    const strain = this.settings.debugStrain && diagnostics ? diagnostics.atom_strain : null;
    for (const site of sites) {
      if (!shown[site.index]) {
        continue;
      }
      const size = this.ballRadius(site, bonded.has(site.index));
      const at = point(site.index);
      radius = Math.max(radius, at.distanceTo(this.centre) + Math.max(size, this.settings.style === "spacefill" ? 0 : 0.3));
      if (size > 0) {
        const colour = new Color(site.colour);
        if (strain) {
          colour.lerp(STRAIN, Math.min(1, (strain[site.index] ?? 0) / STRAIN_SATURATES_AT));
        }
        const ball = new Mesh(this.unitSphere, new MeshStandardMaterial({ color: colour, roughness: 0.5 }));
        ball.position.copy(at);
        ball.scale.setScalar(size);
        const part = this.addPart("atom", site.implied ? null : site.atom, { element: site.element }, ball);
        this.atomParts.set(site.index, part);
        this.content.add(ball);
      }
    }

    if (this.settings.style !== "spacefill") {
      const aromaticRings = this.aromaticCentres(links, point);
      for (const link of links) {
        this.addBond(link, sites, point, neighbours, aromaticRings.get(link));
      }
    }

    if (this.settings.charges) {
      for (const site of sites) {
        if (shown[site.index] && site.charge !== 0) {
          this.addCharge(site, point(site.index), this.ballRadius(site, bonded.has(site.index)));
        }
      }
    }
    if (this.settings.axes) {
      this.addAxes();
    }
    if (diagnostics) {
      this.addDebug(diagnostics, point, shown);
    }

    this.radius = radius + (this.settings.axes ? AXIS_LENGTH / 2 : 0.5);
    this.scene.add(this.content);
    this.refreshEmphasis();
    this.requestRender();
  }

  private ballRadius(site: Site3D, bonded: boolean): number {
    switch (this.settings.style) {
      case "spacefill":
        return site.radius;
      case "ball_and_stick":
        return BALL_FRACTION * site.radius;
      default:
        return bonded ? 0 : LONE_ATOM_RADIUS;
    }
  }

  private addPart(kind: "atom" | "bond", index: number | null, data: Record<string, string>, object: Object3D): Part {
    const part: Part = { pick: { kind, index, data, handle: object }, object, index: index ?? -1 };
    object.userData.part = part;
    this.parts.push(part);
    return part;
  }

  /** The centre of the ring system each aromatic bond is in, so the dashed line can sit on the inside. */
  private aromaticCentres(links: Connection3D[], point: (index: number) => Vector3): Map<Connection3D, Vector3> {
    const aromatic = links.filter((link) => link.order === "AROMATIC");
    const touching = new Map<number, Connection3D[]>();
    for (const link of aromatic) {
      touching.set(link.first, [...(touching.get(link.first) ?? []), link]);
      touching.set(link.second, [...(touching.get(link.second) ?? []), link]);
    }

    const centres = new Map<Connection3D, Vector3>();
    for (const start of aromatic) {
      if (centres.has(start)) {
        continue;
      }
      const group = new Set<Connection3D>([start]);
      const atoms = new Set<number>();
      const queue = [start];
      while (queue.length > 0) {
        const link = queue.pop() as Connection3D;
        for (const atom of [link.first, link.second]) {
          atoms.add(atom);
          for (const next of touching.get(atom) ?? []) {
            if (!group.has(next)) {
              group.add(next);
              queue.push(next);
            }
          }
        }
      }
      const centre = new Vector3();
      atoms.forEach((atom) => centre.add(point(atom)));
      centre.divideScalar(atoms.size);
      group.forEach((link) => centres.set(link, centre));
    }
    return centres;
  }

  private addBond(
    link: Connection3D,
    sites: Site3D[],
    point: (index: number) => Vector3,
    neighbours: Map<number, number[]>,
    ringCentre: Vector3 | undefined,
  ): void {
    const from = point(link.first);
    const to = point(link.second);
    const along = to.clone().sub(from);
    if (along.lengthSq() < 1e-12) {
      return;
    }
    along.normalize();

    const stick = this.settings.style === "sticks";
    const thin = this.settings.style === "wireframe";
    const width = stick ? 0.15 : thin ? 0.02 : 0.07;
    const spacing = stick ? 0.2 : thin ? 0.08 : 0.14;

    const across = this.across(link, from, to, along, point, neighbours, ringCentre);
    const group = new Group();
    const lines: { offset: number; dashed: boolean; width: number }[] = (() => {
      switch (link.order) {
        case "DOUBLE":
          return [-0.5, 0.5].map((side) => ({ offset: side * spacing, dashed: false, width: width * 0.7 }));
        case "TRIPLE":
          return [-1, 0, 1].map((side) => ({ offset: side * spacing, dashed: false, width: width * 0.6 }));
        case "QUADRUPLE":
          return [-1.5, -0.5, 0.5, 1.5].map((side) => ({ offset: side * spacing, dashed: false, width: width * 0.5 }));
        case "AROMATIC":
          return [
            { offset: 0, dashed: false, width },
            { offset: spacing * 1.4, dashed: true, width: width * 0.7 },
          ];
        default:
          return [{ offset: 0, dashed: false, width }];
      }
    })();

    const first = new Color(sites[link.first]?.colour ?? "#888888");
    const second = new Color(sites[link.second]?.colour ?? "#888888");
    for (const line of lines) {
      const shift = across.clone().multiplyScalar(line.offset);
      const a = from.clone().add(shift);
      const b = to.clone().add(shift);
      if (line.dashed) {
        const inset = a.distanceTo(b) * 0.15;
        a.addScaledVector(along, inset);
        b.addScaledVector(along, -inset);
        this.dashes(group, a, b, line.width, first, second);
      } else {
        const middle = a.clone().add(b).multiplyScalar(0.5);
        group.add(this.cylinder(a, middle, line.width, new MeshStandardMaterial({ color: first, roughness: 0.5 })));
        group.add(this.cylinder(middle, b, line.width, new MeshStandardMaterial({ color: second, roughness: 0.5 })));
      }
    }

    const part = this.addPart("bond", link.bond >= 0 ? link.bond : null, { order: link.order.toLowerCase() }, group);
    group.traverse((object) => {
      object.userData.part = part;
    });
    this.content.add(group);
  }

  /** Which way a bond's extra lines are laid out: towards the ring, or into the plane of a neighbour. */
  private across(
    link: Connection3D,
    from: Vector3,
    to: Vector3,
    along: Vector3,
    point: (index: number) => Vector3,
    neighbours: Map<number, number[]>,
    ringCentre: Vector3 | undefined,
  ): Vector3 {
    const candidates: Vector3[] = [];
    if (ringCentre) {
      candidates.push(ringCentre.clone().sub(from.clone().add(to).multiplyScalar(0.5)));
    }
    for (const [atom, other] of [[link.first, link.second], [link.second, link.first]] as const) {
      for (const next of neighbours.get(atom) ?? []) {
        if (next !== other) {
          candidates.push(point(next).sub(point(atom)));
        }
      }
    }
    for (const candidate of candidates) {
      const flat = candidate.addScaledVector(along, -candidate.dot(along));
      if (flat.lengthSq() > 1e-6) {
        return flat.normalize();
      }
    }
    const any = Math.abs(along.x) < 0.9 ? new Vector3(1, 0, 0) : new Vector3(0, 1, 0);
    return along.clone().cross(any).normalize();
  }

  private dashes(group: Group, a: Vector3, b: Vector3, width: number, first: Color, second: Color): void {
    const length = a.distanceTo(b);
    const period = 0.2;
    const count = Math.max(1, Math.round(length / period));
    const step = length / count;
    const direction = b.clone().sub(a).normalize();
    for (let i = 0; i < count; i++) {
      const start = a.clone().addScaledVector(direction, i * step + step * 0.15);
      const end = a.clone().addScaledVector(direction, i * step + step * 0.85);
      const colour = i < count / 2 ? first : second;
      group.add(this.cylinder(start, end, width, new MeshStandardMaterial({ color: colour, roughness: 0.5 })));
    }
  }

  private cylinder(a: Vector3, b: Vector3, radius: number, material: MeshStandardMaterial | MeshBasicMaterial): Mesh {
    const mesh = new Mesh(this.unitCylinder, material);
    const along = b.clone().sub(a);
    const length = along.length();
    mesh.position.copy(a).addScaledVector(along, 0.5);
    if (length > 1e-9) {
      mesh.quaternion.copy(new Quaternion().setFromUnitVectors(UP, along.normalize()));
    }
    mesh.scale.set(radius, Math.max(length, 1e-6), radius);
    return mesh;
  }

  private label(text: string, colour: string, height: number): Sprite {
    const key = `${text}|${colour}`;
    let texture = this.textures.get(key);
    if (!texture) {
      const canvas = document.createElement("canvas");
      const font = "bold 64px sans-serif";
      const context = canvas.getContext("2d") as CanvasRenderingContext2D;
      context.font = font;
      canvas.width = Math.ceil(context.measureText(text).width) + 16;
      canvas.height = 96;
      context.font = font;
      context.textAlign = "center";
      context.textBaseline = "middle";
      context.lineWidth = 12;
      context.strokeStyle = "#ffffff";
      context.lineJoin = "round";
      context.strokeText(text, canvas.width / 2, canvas.height / 2);
      context.fillStyle = colour;
      context.fillText(text, canvas.width / 2, canvas.height / 2);
      texture = new CanvasTexture(canvas);
      this.textures.set(key, texture);
    }
    const image = texture.image as HTMLCanvasElement;
    const sprite = new Sprite(new SpriteMaterial({ map: texture, depthTest: false, transparent: true }));
    sprite.scale.set((height * image.width) / image.height, height, 1);
    sprite.renderOrder = 10;
    return sprite;
  }

  private addCharge(site: Site3D, at: Vector3, radius: number): void {
    const size = Math.abs(site.charge);
    const text = (size === 1 ? "" : String(size)) + (site.charge > 0 ? "+" : "\u2212");
    const sprite = this.label(text, site.charge > 0 ? "#1f4fd8" : "#c0392b", 0.5);
    // anchoring the sprite's lower left at the atom's centre, then pulling the anchor back, puts it at the upper right on screen
    const pull = Math.max(radius, 0.15) * 0.75;
    sprite.center.set(-pull / sprite.scale.x, -pull / sprite.scale.y);
    sprite.position.copy(at);
    this.content.add(sprite);
  }

  /** Axes sit on the point the view orbits, so they show where the pivot is. */
  private addAxes(): void {
    const colours = [0xe41a1c, 0x2ca02c, 0x1f4fd8];
    const axes = new Group();
    [new Vector3(1, 0, 0), new Vector3(0, 1, 0), new Vector3(0, 0, 1)].forEach((direction, i) => {
      axes.add(new ArrowHelper(direction, new Vector3(), AXIS_LENGTH, colours[i], 0.5, 0.36));
    });
    this.axes = axes;
    this.syncAxes();
    this.content.add(axes);
  }

  private syncAxes(): void {
    this.axes?.position.copy(this.controls.target);
  }

  private addDebug(
    diagnostics: NonNullable<Structure3D["conformers"][number]["diagnostics"]>,
    point: (index: number) => Vector3,
    shown: boolean[],
  ): void {
    const { tolerances } = this.structure;
    const over = (deviation: number, tolerance: number) => deviation / tolerance >= this.settings.debugThreshold;
    const visible = (...atoms: number[]) => atoms.every((atom) => shown[atom]);
    const line = (a: Vector3, b: Vector3, colour: string, width = 0.02) =>
      this.content.add(this.cylinder(a, b, width, new MeshBasicMaterial({ color: colour, depthTest: false })));

    if (this.settings.debugBonds) {
      for (const length of diagnostics.lengths) {
        if (visible(length.first, length.second) && over(Math.abs(length.actual - length.ideal), tolerances.length)) {
          const a = point(length.first);
          const b = point(length.second);
          line(a, b, "#e02020", 0.04);
          const note = this.label(`${length.actual.toFixed(3)} \u00c5`, "#c00000", 0.22);
          note.position.copy(a.clone().add(b).multiplyScalar(0.5));
          this.content.add(note);
        }
      }
    }

    if (this.settings.debugAngles) {
      for (const angle of diagnostics.angles) {
        if (visible(angle.first, angle.middle, angle.second) && over(Math.abs(angle.actual - angle.ideal), tolerances.angle)) {
          this.arc(point(angle.middle), point(angle.first), point(angle.second), `${angle.actual.toFixed(1)}\u00b0`);
        }
      }
    }

    if (this.settings.debugTorsions) {
      const worst = new Map<string, (typeof diagnostics.torsions)[number]>();
      for (const torsion of diagnostics.torsions) {
        const deviation = ThreeView.turn(torsion.actual - torsion.ideal);
        const key = `${Math.min(torsion.second, torsion.third)}-${Math.max(torsion.second, torsion.third)}`;
        const before = worst.get(key);
        if (visible(torsion.first, torsion.second, torsion.third, torsion.fourth) && over(deviation, tolerances.torsion)
          && (!before || deviation > ThreeView.turn(before.actual - before.ideal))) {
          worst.set(key, torsion);
        }
      }
      for (const torsion of worst.values()) {
        line(point(torsion.first), point(torsion.fourth), "#8e44ad");
        const note = this.label(`${torsion.actual.toFixed(1)}\u00b0`, "#6c3483", 0.22);
        note.position.copy(point(torsion.second).add(point(torsion.third)).multiplyScalar(0.5));
        this.content.add(note);
      }
    }

    if (this.settings.debugClashes) {
      for (const clash of diagnostics.clashes) {
        if (visible(clash.first, clash.second) && over(clash.contact - clash.distance, tolerances.clash)) {
          const a = point(clash.first);
          const b = point(clash.second);
          line(a, b, "#e67e22", 0.03);
          const note = this.label(`${(clash.contact - clash.distance).toFixed(2)} \u00c5`, "#a04000", 0.22);
          note.position.copy(a.clone().add(b).multiplyScalar(0.5));
          this.content.add(note);
        }
      }
    }

    if (this.settings.debugLonePairs) {
      for (const pair of diagnostics.lone_pairs) {
        if (shown[pair.node]) {
          const tip = new Vector3(pair.tip.x, pair.tip.y, pair.tip.z);
          line(point(pair.node), tip, "#2e86c1", 0.015);
          const lobe = new Mesh(this.unitSphere, new MeshBasicMaterial({ color: "#2e86c1", transparent: true, opacity: 0.4 }));
          lobe.position.copy(tip);
          lobe.scale.setScalar(0.12);
          this.content.add(lobe);
        }
      }
    }
  }

  /** An arc between two bonds at `middle`, labelled with the angle. */
  private arc(middle: Vector3, a: Vector3, b: Vector3, text: string): void {
    const u = a.clone().sub(middle).normalize();
    const v = b.clone().sub(middle).normalize();
    const sweep = Math.acos(Math.min(1, Math.max(-1, u.dot(v))));
    const w = v.clone().addScaledVector(u, -v.dot(u));
    if (w.lengthSq() < 1e-9) {
      return;
    }
    w.normalize();

    const at = (fraction: number, radius: number) =>
      middle.clone()
        .addScaledVector(u, Math.cos(sweep * fraction) * radius)
        .addScaledVector(w, Math.sin(sweep * fraction) * radius);
    const steps = 16;
    for (let i = 0; i < steps; i++) {
      this.content.add(
        this.cylinder(at(i / steps, 0.45), at((i + 1) / steps, 0.45), 0.02, new MeshBasicMaterial({ color: "#d35400", depthTest: false })),
      );
    }
    const note = this.label(text, "#a04000", 0.22);
    note.position.copy(at(0.5, 0.7));
    this.content.add(note);
  }

  private static turn(degrees: number): number {
    const difference = Math.abs(degrees) % 360;
    return difference > 180 ? 360 - difference : difference;
  }

  /** Tints what is hovered, what ends a hovered bond, and what is selected. */
  private refreshEmphasis(): void {
    const tints = new Map<Object3D, Color>();
    for (const part of this.parts) {
      const selected = part.index >= 0 && (part.pick.kind === "atom" ? this.selection.atoms : this.selection.bonds).has(part.index);
      if (selected) {
        tints.set(part.object, SELECTED);
      }
    }
    for (const end of this.highlights.ends) {
      for (const part of this.atomParts.values()) {
        if (part.index === end.atom) {
          tints.set(part.object, END_COLOURS[end.colour] ?? HOVER);
        }
      }
    }
    const hover = this.highlights.hover?.handle;
    if (hover instanceof Object3D) {
      tints.set(hover, HOVER);
    }

    for (const part of this.parts) {
      const tint = tints.get(part.object);
      part.object.traverse((object) => {
        if (object instanceof Mesh && object.material instanceof MeshStandardMaterial) {
          if (tint) {
            object.material.emissive.copy(tint);
            object.material.emissiveIntensity = 0.55;
          } else {
            object.material.emissive.setScalar(0);
            object.material.emissiveIntensity = 1;
          }
        }
      });
    }
    this.requestRender();
  }

  private partAt(event: PointerEvent): Part | null {
    const box = this.renderer.domElement.getBoundingClientRect();
    const pointer = new Vector2(((event.clientX - box.left) / box.width) * 2 - 1, -((event.clientY - box.top) / box.height) * 2 + 1);
    this.raycaster.setFromCamera(pointer, this.camera);
    const hits = this.raycaster.intersectObjects(this.parts.map((part) => part.object), true);
    for (const hit of hits) {
      const part = hit.object.userData.part as Part | undefined;
      if (part) {
        return part;
      }
    }
    return null;
  }

  private readonly onMove = (event: PointerEvent): void => {
    if (event.buttons !== 0) {
      return;
    }
    const part = this.partAt(event);
    this.listeners.forEach((listener) => listener.hover?.(part?.pick ?? null, { x: event.clientX, y: event.clientY }));
  };

  private readonly onLeave = (): void => {
    this.listeners.forEach((listener) => listener.hover?.(null, { x: 0, y: 0 }));
  };

  private readonly onDown = (event: PointerEvent): void => {
    this.rightDown = event.button === 2 ? { x: event.clientX, y: event.clientY } : null;
  };

  /** A right-click that is not a pan pivots on the atom nearest what was clicked, or back on the middle if nothing was. */
  private readonly onUp = (event: PointerEvent): void => {
    const down = this.rightDown;
    this.rightDown = null;
    if (event.button !== 2 || !down || Math.hypot(event.clientX - down.x, event.clientY - down.y) > 4) {
      return;
    }
    const hit = this.hitAt(event);
    let nearest: Vector3 | null = null;
    let best = Infinity;
    if (hit) {
      for (const { at } of this.sitePoints.values()) {
        const distance = at.distanceToSquared(hit);
        if (distance < best) {
          best = distance;
          nearest = at;
        }
      }
    }
    this.pivotOn(nearest ?? this.centre);
  };

  /** Moves the point the view orbits to `to`, carrying the camera with it so that the view keeps its angle. */
  private pivotOn(to: Vector3): void {
    this.camera.position.add(to.clone().sub(this.controls.target));
    this.controls.target.copy(to);
    this.syncAxes();
    this.controls.update();
    this.requestRender();
  }

  private hitAt(event: PointerEvent): Vector3 | null {
    const box = this.renderer.domElement.getBoundingClientRect();
    const pointer = new Vector2(((event.clientX - box.left) / box.width) * 2 - 1, -((event.clientY - box.top) / box.height) * 2 + 1);
    this.raycaster.setFromCamera(pointer, this.camera);
    const hits = this.raycaster.intersectObjects(this.parts.map((part) => part.object), true);
    return hits.find((hit) => hit.object.userData.part)?.point ?? null;
  }

  private resize(): void {
    const width = Math.max(1, this.element.clientWidth);
    const height = Math.max(1, this.element.clientHeight);
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.requestRender();
  }

  private requestRender(): void {
    if (this.frame === 0) {
      this.frame = requestAnimationFrame(() => {
        this.frame = 0;
        this.renderer.render(this.scene, this.camera);
      });
    }
  }
}
