import { Box3, Color, Group, Mesh, MeshStandardMaterial, Raycaster, type Sprite, Vector3 } from "three";
import type { Vec3 } from "../../api/types/structure";
import { aromaticCentres } from "../../render/aromatic";
import { makeAxes } from "../../render/axes";
import { ballRadius, buildBond, type BondEnds, type Style } from "../../render/bonds";
import { disposeMaterials } from "../../render/dispose";
import { type Part, PartRegistry } from "../../render/parts";
import { Primitives } from "../../render/primitives";
import { Stage } from "../../render/stage";
import type { SessionAtom, SessionBond, SessionState } from "../session/messages";

/** An atom or bond under the pointer, named by the identifier the session gave it. */
export interface StudioPick {
  kind: "atom" | "bond";
  id: string;
}

/** Where the pointer is, in pixels. */
export interface ScreenPoint {
  x: number;
  y: number;
}

export interface StudioViewListener {
  /** The pointer moved over `pick`, or over nothing if it is null. Not called while a button is held. */
  hover?(pick: StudioPick | null, at: ScreenPoint): void;
  /** A left click that was not a camera drag. `additive` is whether shift was held. */
  click?(pick: StudioPick | null, additive: boolean): void;
}

/** A bond as the drawing holds it: which atoms it joins by position in the atom list, so the shared drawing code can take it. */
interface Link extends BondEnds {
  bond: SessionBond;
}

interface AtomNode {
  atom: SessionAtom;
  index: number;
  /** Where the atom is. The ball and the charge badge both read this, so moving it moves them. */
  at: Vector3;
  ball: Mesh;
  badge: Sprite | null;
  /** The indices of the atoms bonded to this one. */
  neighbours: number[];
}

interface BondNode {
  link: Link;
  drawn: { group: Group; part: Part } | null;
}

/** How far the pointer may move between pressing and releasing and still count as a click. */
const CLICK_SLOP = 4;

const STYLE: Style = "ball_and_stick";

/** Draws a Studio session on a canvas. */
export class StudioView {
  private readonly stage: Stage;
  private readonly kit = new Primitives();
  private readonly registry = new PartRegistry();
  private readonly raycaster = new Raycaster();
  private readonly content = new Group();
  private readonly axes = makeAxes();
  private readonly listeners = new Set<StudioViewListener>();

  private atoms: AtomNode[] = [];
  private bonds: BondNode[] = [];
  private atomById = new Map<string, AtomNode>();
  private bondsOf = new Map<number, BondNode[]>();
  private ringCentres = new Map<Link, Vector3>();
  /** The atoms bonded to each atom, by position in the atom list, as the drawing code wants them. */
  private neighbourMap = new Map<number, number[]>();
  /** A fingerprint of everything about the structure except where its atoms are. */
  private signature = "";
  /** The session the camera was last framed on, so a new session is framed and an edit to the same one is not. */
  private framedSession: string | null = null;
  private down: { x: number; y: number } | null = null;

  /**
   * @param canvas the canvas to draw on; it is sized to its CSS box
   */
  constructor(canvas: HTMLCanvasElement) {
    this.stage = new Stage({ canvas, sizedBy: canvas });
    this.stage.scene.add(this.content, this.axes);

    canvas.addEventListener("pointermove", this.onMove);
    canvas.addEventListener("pointerleave", this.onLeave);
    canvas.addEventListener("pointerdown", this.onDown);
    canvas.addEventListener("pointerup", this.onUp);
    canvas.addEventListener("pointercancel", this.onCancel);

    this.frameScene();
  }

  /**
   * Shows a session's state.
   *
   * @param state the state to show; the camera is framed on it the first time a session is shown
   */
  setState(state: SessionState): void {
    const signature = signatureOf(state);
    if (signature === this.signature) {
      this.moveAtoms(state.atoms.map((atom) => [atom.id, atom.position]));
      return;
    }

    this.signature = signature;
    this.build(state);
    if (this.framedSession !== state.session_id) {
      this.framedSession = state.session_id;
      this.frameScene();
    }
  }

  /**
   * Moves one atom, redrawing it and only the bonds its position affects.
   *
   * @param id the atom's identifier
   * @param position where it now is, in angstroms
   */
  moveAtom(id: string, position: Vec3): void {
    this.moveAtoms([[id, position]]);
  }

  /**
   * Moves atoms, redrawing them and only the bonds their positions affect.
   *
   * @param moves each atom's identifier and where it now is; unknown identifiers and atoms already there are skipped
   */
  moveAtoms(moves: Iterable<readonly [string, Vec3]>): void {
    const moved = new Set<AtomNode>();
    for (const [id, position] of moves) {
      const node = this.atomById.get(id);
      if (node && (node.at.x !== position.x || node.at.y !== position.y || node.at.z !== position.z)) {
        node.at.set(position.x, position.y, position.z);
        node.ball.position.copy(node.at);
        moved.add(node);
      }
    }
    if (moved.size === 0) {
      return;
    }

    // a bond's extra lines fan out towards its neighbours' atoms, so the bonds one step out turn as well as the ones at the atom
    const affected = new Set<BondNode>();
    for (const node of moved) {
      for (const index of [node.index, ...node.neighbours]) {
        this.bondsOf.get(index)?.forEach((bond) => affected.add(bond));
      }
    }
    if ([...affected].some((bond) => bond.link.order === "AROMATIC")) {
      // a ring's centre is the mean of all of it, so every aromatic bond may have shifted
      this.ringCentres = aromaticCentres(this.bonds.map((bond) => bond.link), this.point);
      this.bonds.filter((bond) => bond.link.order === "AROMATIC").forEach((bond) => affected.add(bond));
    }
    affected.forEach((bond) => this.redraw(bond));
    this.stage.requestRender();
  }

  /** Points the camera at the whole scene. */
  frameScene(): void {
    this.frameAtoms(this.atoms.map((node) => node.atom.id));
  }

  /**
   * Points the camera at some atoms, orbiting their centre.
   *
   * @param ids the atoms to show; if none of them are drawn, the whole scene is framed
   */
  frameAtoms(ids: readonly string[]): void {
    const nodes = ids.flatMap((id) => this.atomById.get(id) ?? []);
    if (nodes.length === 0 && this.atoms.length > 0) {
      this.frameScene();
      return;
    }
    if (nodes.length === 0) {
      this.stage.frame(new Vector3(), 3);
      return;
    }

    const box = new Box3();
    nodes.forEach((node) => box.expandByPoint(node.at));
    const centre = box.getCenter(new Vector3());
    let radius = 1;
    for (const node of nodes) {
      radius = Math.max(radius, node.at.distanceTo(centre) + Math.max(ballRadius(node.atom.radius, STYLE, true), 0.3));
    }
    this.stage.frame(centre, radius + 0.5);
  }

  /**
   * Shows or hides the scene axes.
   *
   * @param visible whether to show them
   */
  setAxes(visible: boolean): void {
    this.axes.visible = visible;
    this.stage.requestRender();
  }

  /**
   * Hears about the pointer.
   *
   * @param listener who to tell
   * @returns how to stop listening
   */
  listen(listener: StudioViewListener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /** Releases the canvas, the renderer and everything drawn. */
  dispose(): void {
    const canvas = this.stage.canvas;
    canvas.removeEventListener("pointermove", this.onMove);
    canvas.removeEventListener("pointerleave", this.onLeave);
    canvas.removeEventListener("pointerdown", this.onDown);
    canvas.removeEventListener("pointerup", this.onUp);
    canvas.removeEventListener("pointercancel", this.onCancel);
    this.clear();
    disposeMaterials(this.axes);
    this.kit.dispose();
    this.stage.dispose();
    this.listeners.clear();
  }

  /** Where an atom is, by position in the atom list. A copy, since the drawing code moves the points it is given. */
  private readonly point = (index: number): Vector3 => (this.atoms[index]?.at ?? new Vector3()).clone();

  private build(state: SessionState): void {
    this.clear();

    const index = new Map(state.atoms.map((atom, i) => [atom.id, i]));
    this.atoms = state.atoms.map((atom, i) => this.drawAtom(atom, i));
    this.atomById = new Map(this.atoms.map((node) => [node.atom.id, node]));

    const links: Link[] = [];
    for (const bond of state.bonds) {
      const first = index.get(bond.first);
      const second = index.get(bond.second);
      if (first !== undefined && second !== undefined) {
        links.push({ bond, first, second, order: bond.order });
      }
    }
    for (const link of links) {
      this.atoms[link.first]?.neighbours.push(link.second);
      this.atoms[link.second]?.neighbours.push(link.first);
    }

    this.neighbourMap = new Map(this.atoms.map((node) => [node.index, node.neighbours]));
    this.ringCentres = aromaticCentres(links, this.point);
    this.bonds = links.map((link) => ({ link, drawn: null }));
    this.bondsOf = new Map();
    for (const bond of this.bonds) {
      for (const end of [bond.link.first, bond.link.second]) {
        this.bondsOf.set(end, [...(this.bondsOf.get(end) ?? []), bond]);
      }
      this.redraw(bond);
    }
    this.stage.requestRender();
  }

  private drawAtom(atom: SessionAtom, index: number): AtomNode {
    const at = new Vector3(atom.position.x, atom.position.y, atom.position.z);
    const radius = ballRadius(atom.radius, STYLE, true);
    const ball = new Mesh(this.kit.sphereGeometry, new MeshStandardMaterial({ color: new Color(atom.colour), roughness: 0.5 }));
    ball.position.copy(at);
    ball.scale.setScalar(radius);
    this.registry.add("atom", null, { element: atom.element }, ball, atom.id);
    this.content.add(ball);

    let badge: Sprite | null = null;
    if (atom.charge !== 0) {
      badge = this.kit.labels.chargeBadge(this.content, atom.charge, at, radius);
      this.content.add(badge);
    }
    return { atom, index, at, ball, badge, neighbours: [] };
  }

  /** Draws a bond afresh from where its atoms are now, replacing what was drawn. */
  private redraw(bond: BondNode): void {
    if (bond.drawn) {
      this.content.remove(bond.drawn.group);
      disposeMaterials(bond.drawn.group);
      this.registry.remove(bond.drawn.part);
      bond.drawn = null;
    }

    const group = buildBond(
      this.kit,
      bond.link,
      this.atoms.map((node) => node.atom),
      STYLE,
      this.point,
      this.neighbourMap,
      this.ringCentres.get(bond.link),
    );
    if (!group) {
      return;
    }
    const part = this.registry.add("bond", null, { order: bond.link.order.toLowerCase() }, group, bond.link.bond.id);
    this.registry.claimChildren(part);
    this.content.add(group);
    bond.drawn = { group, part };
  }

  private clear(): void {
    disposeMaterials(this.content);
    this.content.clear();
    this.registry.clear();
    this.atoms = [];
    this.bonds = [];
    this.atomById = new Map();
    this.bondsOf = new Map();
    this.ringCentres = new Map();
    this.neighbourMap = new Map();
  }

  private pickAt(event: PointerEvent): StudioPick | null {
    const hit = this.registry.cast(this.raycaster, this.stage.camera, this.stage.pointer(event));
    const id = hit?.part.pick.id;
    return hit && id !== undefined ? { kind: hit.part.pick.kind, id } : null;
  }

  private readonly onMove = (event: PointerEvent): void => {
    if (event.buttons !== 0) {
      return;
    }
    const pick = this.pickAt(event);
    this.listeners.forEach((listener) => listener.hover?.(pick, { x: event.clientX, y: event.clientY }));
  };

  private readonly onLeave = (): void => {
    this.listeners.forEach((listener) => listener.hover?.(null, { x: 0, y: 0 }));
  };

  private readonly onDown = (event: PointerEvent): void => {
    this.down = event.button === 0 ? { x: event.clientX, y: event.clientY } : null;
  };

  private readonly onUp = (event: PointerEvent): void => {
    const down = this.down;
    this.down = null;
    // a left drag orbits the camera, and the release that ends one must not also select what is under it
    if (event.button !== 0 || !down || Math.hypot(event.clientX - down.x, event.clientY - down.y) > CLICK_SLOP) {
      return;
    }
    const pick = this.pickAt(event);
    this.listeners.forEach((listener) => listener.click?.(pick, event.shiftKey));
  };

  private readonly onCancel = (): void => {
    this.down = null;
  };
}

/** Everything about a state that decides how it is drawn, other than the atoms' positions. */
function signatureOf(state: SessionState): string {
  const atoms = state.atoms.map((atom) => `${atom.id}:${atom.element}:${atom.charge}:${atom.colour}:${atom.radius}`);
  const bonds = state.bonds.map((bond) => `${bond.id}:${bond.first}:${bond.second}:${bond.order}`);
  return `${atoms.join(",")}|${bonds.join(",")}`;
}
