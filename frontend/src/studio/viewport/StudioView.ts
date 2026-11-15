import { Box3, Color, Group, Mesh, MeshStandardMaterial, type Object3D, Quaternion, type Ray, Raycaster, type Sprite, Vector3 } from "three";
import type { Vec3 } from "../../api/types/structure";
import { aromaticCentres } from "../../render/aromatic";
import { makeAxes } from "../../render/axes";
import { ballRadius, buildBond, type BondEnds, type Style } from "../../render/bonds";
import { disposeMaterials } from "../../render/dispose";
import { applyTints, HOVER_TINT, SELECTED_TINT } from "../../render/emphasis";
import { type Part, PartRegistry } from "../../render/parts";
import { Primitives } from "../../render/primitives";
import { Stage } from "../../render/stage";
import { Triad } from "../../render/triad";
import { type Constraint, dragged, pointOnConstraint } from "./dragPlane";
import { type Guide, makeGuide } from "./guides";
import type { AtomMove, SessionAtom, SessionBond, SessionState } from "../session/messages";

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
  /** A left click that was not a camera drag. `additive` is whether shift, control or command was held. */
  click?(pick: StudioPick | null, additive: boolean): void;
  /** An atom has been picked up by the move tool and has begun to move, along with the rest of the selection if it is in it. */
  dragStart?(id: string): void;
  /** The dragged atoms are at `moves`' positions. `done` is true for the last report, when the pointer is released. */
  drag?(moves: AtomMove[], done: boolean): void;
  /** A drag was abandoned, and the atoms put back at `moves`' positions, where they were picked up. */
  dragCancel?(moves: AtomMove[]): void;
}

/** Atoms and bonds, by identifier, to draw with emphasis. */
export interface Emphasis {
  atoms: ReadonlySet<string>;
  bonds: ReadonlySet<string>;
}

/** What to tint, strongest last: what is selected, the active one among them, and what is hovered. */
export interface Emphasised {
  selected: Emphasis;
  active: Emphasis;
  hover: Emphasis;
}

const NO_EMPHASIS: Emphasis = { atoms: new Set(), bonds: new Set() };

/** The tint of the active item: the selection's blue, brightened so that it stands out from the rest. */
const ACTIVE_TINT = SELECTED_TINT.clone().lerp(new Color("#ffffff"), 0.45);

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

/** How far, in radians, the camera may have turned since a triad click and still count as not having moved. */
const SAME_VIEW = 1e-3;

/** How far the pointer may move between pressing and releasing and still count as a click. */
const CLICK_SLOP = 4;

const STYLE: Style = "ball_and_stick";

/** An atom that moves with a drag. */
interface Member {
  node: AtomNode;
  /** Where the atom was when it was pressed, which a cancelled drag puts it back to. */
  original: Vector3;
  /** Where the atom was when the current constraint began, which the drag is measured from. */
  start: Vector3;
}

/**
 * An atom the pointer is pressed on while the move tool is in use, which becomes a drag once the pointer has moved.
 * If the atom is one of several selected, the rest are carried with it, each keeping its offset from the others.
 */
interface Grab {
  /** The atom the pointer is on. */
  id: string;
  /** The atoms that move, with the one the pointer is on first. */
  members: Member[];
  /** Where the pointer's ray met the constraint when it began. */
  point: Vector3;
  /** The way the camera looks, which does not change during a drag because the camera is not moved. */
  viewDirection: Vector3;
  /** Where the pointer was pressed, in pixels. */
  from: ScreenPoint;
  /** The pointer's latest ray, kept so that a change of constraint can start from where the pointer is. */
  ray: Ray;
  dragging: boolean;
}

/** Draws a Studio session on a canvas. */
export class StudioView {
  private readonly stage: Stage;
  private readonly kit = new Primitives();
  private readonly triad = new Triad(this.kit.labels);
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
  private locked: ReadonlySet<string> = new Set();
  /** Whether pressing on an atom picks it up. */
  private moving = false;
  private constraint: Constraint = "free";
  private grab: Grab | null = null;
  /** The last view a triad click snapped to, so that clicking the same letter again can flip to the far side. */
  private snapped: { axis: Vector3; sign: 1 | -1; orientation: Quaternion } | null = null;
  /** Whether to draw how a move would go. */
  private debug = false;
  private guide: Guide | null = null;
  private emphasis: Emphasised = { selected: NO_EMPHASIS, active: NO_EMPHASIS, hover: NO_EMPHASIS };

  /**
   * @param canvas the canvas to draw on; it is sized to its CSS box
   */
  constructor(canvas: HTMLCanvasElement) {
    this.stage = new Stage({ canvas, sizedBy: canvas, onMove: () => this.placeGuide() });
    // off until the viewport says otherwise, so they do not flash on before it does
    this.axes.visible = false;
    this.stage.scene.add(this.content, this.axes);
    this.stage.addOverlay((renderer, camera) => this.triad.draw(renderer, camera));

    canvas.addEventListener("pointermove", this.onMove);
    canvas.addEventListener("pointerleave", this.onLeave);
    // captured, so that this hears the press before the orbit controls do and can keep them from turning the camera
    canvas.addEventListener("pointerdown", this.onTriad, { capture: true });
    canvas.addEventListener("pointerdown", this.onGrab, { capture: true });
    canvas.addEventListener("pointerdown", this.onDown);
    canvas.addEventListener("pointerup", this.onUp);
    canvas.addEventListener("pointercancel", this.onCancel);
    window.addEventListener("keydown", this.onKey, { capture: true });

    this.frameScene();
  }

  /**
   * Shows a session's state.
   *
   * @param state the state to show; the camera is framed on it the first time a session is shown
   */
  setState(state: SessionState): void {
    const signature = signatureOf(state);
    this.locked = new Set(state.locked);
    if (signature === this.signature) {
      // the atom in the user's hand is where the pointer has put it, and the session is only catching up
      const held = new Set(this.grab?.dragging ? this.grab.members.map((member) => member.node.atom.id) : []);
      this.moveAtoms(state.atoms.filter((atom) => !held.has(atom.id)).map((atom) => [atom.id, atom.position]));
      return;
    }

    this.grab = null;
    this.stage.controls.enabled = true;
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
    this.refreshEmphasis();
    this.placeGuide();
  }

  /**
   * Tints what is selected, active and hovered. Hover shows over active, and active over selected.
   *
   * @param emphasis what to tint; the tints are kept across redraws as atoms move
   */
  setEmphasis(emphasis: Emphasised): void {
    this.emphasis = emphasis;
    this.refreshEmphasis();
    this.refreshGuide();
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
    this.triad.visible = visible;
    this.stage.requestRender();
  }

  /**
   * Turns picking atoms up on or off. While on, pressing on an atom that is not locked and dragging moves it, and
   * the camera does not turn; pressing anywhere else still orbits.
   *
   * @param enabled whether pressing on an atom picks it up
   */
  setMoving(enabled: boolean): void {
    this.moving = enabled;
    if (!enabled) {
      this.cancelDrag();
    }
    this.refreshGuide();
  }

  /**
   * Turns the debugging overlays on or off. Today that is the guide for a move: arrows along the axis, or a faint
   * plane, that the atom being moved is held to.
   *
   * @param enabled whether to draw them
   */
  setDebug(enabled: boolean): void {
    this.debug = enabled;
    this.refreshGuide();
  }

  /**
   * Holds a drag to an axis or plane, or lets it go. A drag already begun carries on from where the atom is now.
   *
   * @param constraint what a dragged atom is held to
   */
  setConstraint(constraint: Constraint): void {
    this.constraint = constraint;
    this.refreshGuide();
    const grab = this.grab;
    if (grab?.dragging) {
      grab.members.forEach((member) => (member.start = member.node.at.clone()));
      grab.point = pointOnConstraint(grab.ray, grab.members[0]!.start, constraint, grab.viewDirection) ?? grab.point;
    }
  }

  /** Abandons a drag in progress, putting the atom back where it was picked up. */
  cancelDrag(): void {
    const grab = this.grab;
    if (!grab) {
      return;
    }
    this.releaseGrab();

    if (grab.dragging) {
      const moves = grab.members.map((member): AtomMove => ({ id: member.node.atom.id, position: toVec3(member.original) }));
      this.moveAtoms(moves.map((move) => [move.id, move.position]));
      this.listeners.forEach((listener) => listener.dragCancel?.(moves));
    }
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
    canvas.removeEventListener("pointerdown", this.onTriad, { capture: true });
    canvas.removeEventListener("pointerdown", this.onGrab, { capture: true });
    canvas.removeEventListener("pointerdown", this.onDown);
    canvas.removeEventListener("pointerup", this.onUp);
    canvas.removeEventListener("pointercancel", this.onCancel);
    window.removeEventListener("keydown", this.onKey, { capture: true });
    this.clear();
    this.guide?.dispose();
    this.triad.dispose();
    disposeMaterials(this.axes);
    this.kit.dispose();
    this.stage.dispose();
    this.listeners.clear();
  }

  /** The atom the move guide is drawn on: the one in hand, or else the active one. */
  private guideTarget(): AtomNode | null {
    if (this.grab?.dragging) {
      return this.grab.members[0]!.node;
    }
    const [active] = this.emphasis.active.atoms;
    return active === undefined ? null : (this.atomById.get(active) ?? null);
  }

  /** Draws the guide for the current constraint afresh, or removes it when there is nothing for it to show. */
  private refreshGuide(): void {
    if (this.guide) {
      this.stage.scene.remove(this.guide.group);
      this.guide.dispose();
      this.guide = null;
    }

    if (this.debug && this.moving && this.guideTarget()) {
      this.guide = makeGuide(this.constraint);
      this.stage.scene.add(this.guide.group);
      this.placeGuide();
    }
    this.stage.requestRender();
  }

  /** Moves the guide onto its atom, and turns a camera-facing one to face the camera. */
  private placeGuide(): void {
    const target = this.guideTarget();
    if (!this.guide || !target) {
      return;
    }

    this.guide.group.position.copy(target.at);
    if (this.guide.facesCamera) {
      this.guide.group.quaternion.copy(this.stage.camera.quaternion);
    }
    this.stage.requestRender();
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
    this.refreshEmphasis();
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

  private refreshEmphasis(): void {
    const { selected, active, hover } = this.emphasis;
    const tints = new Map<Object3D, Color>();
    for (const part of this.registry.parts) {
      const id = part.pick.id;
      if (id === undefined) {
        continue;
      }
      const among = (set: Emphasis) => (part.pick.kind === "atom" ? set.atoms : set.bonds).has(id);
      const tint = among(hover) ? HOVER_TINT : among(active) ? ACTIVE_TINT : among(selected) ? SELECTED_TINT : null;
      if (tint) {
        tints.set(part.object, tint);
      }
    }
    applyTints(this.registry.parts, tints);
    this.stage.requestRender();
  }

  private pickAt(event: PointerEvent): StudioPick | null {
    const hit = this.registry.cast(this.raycaster, this.stage.camera, this.stage.pointer(event));
    const id = hit?.part.pick.id;
    return hit && id !== undefined ? { kind: hit.part.pick.kind, id } : null;
  }

  /** The pointer's ray into the scene. */
  private rayOf(event: PointerEvent): Ray {
    this.raycaster.setFromCamera(this.stage.pointer(event), this.stage.camera);
    return this.raycaster.ray.clone();
  }

  /**
   * Where the grabbed atoms are, given where the pointer is.
   *
   * @param grab the drag
   * @param ray the pointer's ray
   * @returns each atom's place, which are all the same distance from where they began, or null if the ray does not meet
   *     what the drag is held to
   */
  private placesOf(grab: Grab, ray: Ray): AtomMove[] | null {
    const [lead] = grab.members as [Member, ...Member[]];
    const point = pointOnConstraint(ray, lead.start, this.constraint, grab.viewDirection);
    if (!point) {
      return null;
    }

    const offset = dragged(grab.point, point, lead.start).sub(lead.start);
    return grab.members.map((member) => ({ id: member.node.atom.id, position: toVec3(member.start.clone().add(offset)) }));
  }

  /** Lets go of the atom in hand, giving the camera its controls back. */
  private releaseGrab(): void {
    this.grab = null;
    this.stage.controls.enabled = true;
    this.refreshGuide();
  }

  private readonly onTriad = (event: PointerEvent): void => {
    if (event.button !== 0) {
      return;
    }
    const axis = this.triad.axisAt(this.stage.canvas.getBoundingClientRect(), event.clientX, event.clientY);
    if (!axis) {
      return;
    }

    // the press is the triad's: it must not orbit the camera, pick up an atom behind it, or clear the selection
    event.stopImmediatePropagation();
    event.preventDefault();

    // the same letter again, with the camera where the last click left it, goes round to the other side
    const last = this.snapped;
    const again = last !== null && last.axis.dot(axis) > 0.99 && this.stage.camera.quaternion.angleTo(last.orientation) < SAME_VIEW;
    const sign = again && last.sign === 1 ? -1 : 1;

    this.stage.lookFrom(axis.clone().multiplyScalar(sign));
    this.snapped = { axis, sign, orientation: this.stage.camera.quaternion.clone() };
  };

  private readonly onGrab = (event: PointerEvent): void => {
    if (!this.moving || event.button !== 0) {
      return;
    }

    const pick = this.pickAt(event);
    const node = pick?.kind === "atom" ? this.atomById.get(pick.id) : undefined;
    if (!pick || !node || this.locked.has(pick.id)) {
      return;
    }

    // an atom in a selection of several takes the rest with it; one outside the selection goes alone
    const selected = this.emphasis.selected.atoms;
    const group = selected.has(pick.id) && selected.size > 1 ? [...selected] : [pick.id];
    const nodes = [node, ...group.filter((id) => id !== pick.id).map((id) => this.atomById.get(id))];
    if (nodes.some((each) => !each || this.locked.has(each.atom.id))) {
      return;
    }

    const ray = this.rayOf(event);
    const viewDirection = this.stage.camera.getWorldDirection(new Vector3());
    const point = pointOnConstraint(ray, node.at, this.constraint, viewDirection);
    if (!point) {
      return;
    }

    this.grab = {
      id: pick.id,
      members: (nodes as AtomNode[]).map((each) => ({ node: each, original: each.at.clone(), start: each.at.clone() })),
      point,
      viewDirection,
      from: { x: event.clientX, y: event.clientY },
      ray,
      dragging: false,
    };

    // the camera must not turn while the atom does, and the pointer must keep reporting if it leaves the canvas
    this.stage.controls.enabled = false;
    this.stage.canvas.setPointerCapture(event.pointerId);
  };

  private readonly onKey = (event: KeyboardEvent): void => {
    if (event.key === "Escape" && this.grab?.dragging) {
      // the key abandons the drag and goes no further, so it does not also clear the selection
      event.stopPropagation();
      this.cancelDrag();
    }
  };

  private readonly onMove = (event: PointerEvent): void => {
    const grab = this.grab;
    if (grab && event.buttons !== 0) {
      if (!grab.dragging) {
        if (Math.hypot(event.clientX - grab.from.x, event.clientY - grab.from.y) <= CLICK_SLOP) {
          return;
        }
        grab.dragging = true;
        this.refreshGuide();
        this.listeners.forEach((listener) => listener.dragStart?.(grab.id));
      }

      grab.ray = this.rayOf(event);
      const moves = this.placesOf(grab, grab.ray);
      if (moves) {
        this.moveAtoms(moves.map((move) => [move.id, move.position]));
        this.listeners.forEach((listener) => listener.drag?.(moves, false));
      }
      return;
    }

    if (event.buttons !== 0) {
      return;
    }
    const overTriad = this.triad.axisAt(this.stage.canvas.getBoundingClientRect(), event.clientX, event.clientY) !== null;
    this.stage.canvas.style.cursor = overTriad ? "pointer" : "";
    const pick = this.pickAt(event);
    this.listeners.forEach((listener) => listener.hover?.(pick, { x: event.clientX, y: event.clientY }));
  };

  private readonly onLeave = (): void => {
    this.stage.canvas.style.cursor = "";
    this.listeners.forEach((listener) => listener.hover?.(null, { x: 0, y: 0 }));
  };

  private readonly onDown = (event: PointerEvent): void => {
    this.down = event.button === 0 ? { x: event.clientX, y: event.clientY } : null;
  };

  private readonly onUp = (event: PointerEvent): void => {
    const grab = this.grab;
    if (grab) {
      this.releaseGrab();
      if (grab.dragging) {
        this.down = null;
        // a pointer that has left what the drag is held to leaves the atoms where they are
        const moves =
          this.placesOf(grab, this.rayOf(event)) ??
          grab.members.map((member): AtomMove => ({ id: member.node.atom.id, position: toVec3(member.node.at) }));
        this.moveAtoms(moves.map((move) => [move.id, move.position]));
        this.listeners.forEach((listener) => listener.drag?.(moves, true));
        return;
      }
    }

    const down = this.down;
    this.down = null;
    // a left drag orbits the camera, and the release that ends one must not also select what is under it
    if (event.button !== 0 || !down || Math.hypot(event.clientX - down.x, event.clientY - down.y) > CLICK_SLOP) {
      return;
    }
    const pick = this.pickAt(event);
    this.listeners.forEach((listener) => listener.click?.(pick, event.shiftKey || event.ctrlKey || event.metaKey));
  };

  private readonly onCancel = (): void => {
    this.down = null;
    this.cancelDrag();
  };
}

function toVec3(point: Vector3): Vec3 {
  return { x: point.x, y: point.y, z: point.z };
}

/** Everything about a state that decides how it is drawn, other than the atoms' positions. */
function signatureOf(state: SessionState): string {
  const atoms = state.atoms.map((atom) => `${atom.id}:${atom.element}:${atom.charge}:${atom.colour}:${atom.radius}`);
  const bonds = state.bonds.map((bond) => `${bond.id}:${bond.first}:${bond.second}:${bond.order}`);
  return `${atoms.join(",")}|${bonds.join(",")}`;
}
