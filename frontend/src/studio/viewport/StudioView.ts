import {
  Box3,
  Color,
  DoubleSide,
  Group,
  LineBasicMaterial,
  type LineSegments,
  Mesh,
  MeshBasicMaterial,
  MeshStandardMaterial,
  MOUSE,
  type Object3D,
  PlaneGeometry,
  Quaternion,
  Ray,
  Raycaster,
  type Sprite,
  TOUCH,
  Vector3
} from "three";
import type {Vec3} from "../../api/types/structure";
import {aromaticCentres} from "../../render/aromatic";
import {makeAxes} from "../../render/axes";
import {ballRadius, type BondEnds, buildBond, type Style} from "../../render/bonds";
import {disposeMaterials} from "../../render/dispose";
import {applyTints, HOVER_TINT, SELECTED_TINT} from "../../render/emphasis";
import {type Part, PartRegistry} from "../../render/parts";
import {Primitives} from "../../render/primitives";
import {sweep} from "../../render/roll";
import {Stage} from "../../render/stage";
import {Triad} from "../../render/triad";
import {type Basis, type Constraint, dragged, pointOnConstraint, STANDARD_BASIS} from "./dragPlane";
import {withTerminalHydrogens} from "./carry";
import {buildGrid, type GridKind, type GridPlaneChoice} from "./grid";
import {RollRing} from "./rollRing";
import {BondGizmo} from "./bondGizmo";
import {type GizmoMode, MoleculeGizmo} from "./gizmo";
import {type Guide, makeGuide} from "./guides";
import type {
  AtomMove,
  BondEdit,
  GeometryResult,
  Pose,
  SessionAtom,
  SessionBond,
  SessionState
} from "../session/messages";
import type {Hold} from "../selection/bondLength";
import type {AngleView, Pivot, Space, TorsionView} from "../selection/ToolModel";
import {torsion} from "../selection/torsion";
import {angleAt, planeNormal} from "../selection/angle";

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
  /** A handle that translates has been taken hold of, so `constraint` is what the move is now held to. */
  constraint?(constraint: Constraint): void;
  /** A handle of the molecule's gizmo has been taken hold of, with the molecule at `from`. */
  moleculeDragStart?(from: Pose): void;
  /** The gizmo has put the molecule at `pose`. `done` is true for the last report, when the handle is let go. */
  moleculeDrag?(pose: Pose, done: boolean): void;
  /** A drag of the molecule was abandoned, and it was put back at `from`, where it was picked up. */
  moleculeDragCancel?(from: Pose): void;
  /** A bond's handle has put the bond at `edit`. `done` is true for the last report, when the handle is let go. */
  bondDrag?(edit: BondEdit, done: boolean): void;
  /** A drag of a bond's handle was abandoned, and the session is to put the bond back. */
  bondDragCancel?(): void;
}

/**
 * A handle on a bond or an atom, and which atom stays where it is: `first` is the bond's first atom, or the angle's `from`,
 * `second` its second atom or `to`, and `both` is neither.
 */
export type BondHandle =
  | {
      /** An arrow along the bond that slides to change its length. */
      mode: "length";
      bond: string;
      hold: Hold;
    }
  | {
      /** A ring about the bond that turns to change its torsion between `from`, on its first atom, and `to`, on its second. */
      mode: "turn";
      bond: string;
      hold: Hold;
      from: string;
      to: string;
    }
  | {
      /** A ring about an atom that turns to change the angle between two of its bonds. */
      mode: "angle";
      centre: string;
      hold: Hold;
      from: string;
      to: string;
    };

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

/** How many selected atoms there can be for the bonds on them to be labelled with their lengths. */
const LENGTH_LABEL_ATOMS = 4;

/** The most bonds that are labelled with their lengths at once. */
const LENGTH_LABEL_BONDS = 12;

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

/** The colour of what marks strain. */
const STRAIN_COLOUR = "#ff2a2a";

/** The colour of the halo on an atom that is only a little strained, which turns to {@link STRAIN_COLOUR} as it gets worse. */
const MILD_STRAIN_COLOUR = new Color("#ffc233");

/** How strained, in tolerances, an atom has to be for its halo to be as strong as it gets. */
const STRAIN_SATURATED = 6;

/** The colour of the bonds that make a strained angle. */
const ANGLE_COLOUR = "#ff9a1f";

/** How far, in radians, the camera may have turned since a triad click and still count as not having moved. */
const SAME_VIEW = 1e-3;

/** How far the pointer may move between pressing and releasing and still count as a click. */
const CLICK_SLOP = 4;

/** The longest, in milliseconds, between two clicks that make a double-click. */
const DOUBLE_CLICK_MS = 350;

/** The side of the square the axes triad is drawn in, and how far it is from the canvas's lower left corner, in CSS pixels. */
const TRIAD_SIZE = 110;
const TRIAD_MARGIN = 24;

/** How far the middle of the triad, which the roll ring goes round, is from the canvas's left and bottom edges. */
const TRIAD_CENTRE = TRIAD_MARGIN + TRIAD_SIZE / 2;

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
  /** The way the camera looks, in the molecule's own frame, which does not change during a drag because the camera is not moved. */
  viewDirection: Vector3;
  /** Where the pointer was pressed, in pixels. */
  from: ScreenPoint;
  /** The pointer's latest ray, in the molecule's own frame, kept so that a change of constraint can start from where the pointer is. */
  ray: Ray;
  dragging: boolean;
}

/** Draws a Studio session on a canvas. */
export class StudioView {
  private readonly stage: Stage;
  private readonly kit = new Primitives();
  private readonly triad = new Triad(this.kit.labels, TRIAD_SIZE, TRIAD_MARGIN);
  /** The dial round the triad that rolls the view. */
  private readonly ring = new RollRing(TRIAD_CENTRE);
  private readonly registry = new PartRegistry();
  private readonly raycaster = new Raycaster();
  /** Holds everything drawn of the molecule, positioned in the scene by its pose. Atoms are placed in this group's own frame. */
  private readonly content = new Group();
  /** Turns a guide to the frame it is drawn in: the scene's, the molecule's, or the camera's. */
  private readonly guideFrame = new Group();
  private readonly gizmo: MoleculeGizmo;
  private readonly bondGizmo: BondGizmo;

  /** The handle on a bond that is being offered, or null for none. */
  private bondHandle: BondHandle | null = null;
  private readonly axes = makeAxes();
  private readonly listeners = new Set<StudioViewListener>();

  private atoms: AtomNode[] = [];
  private bonds: BondNode[] = [];
  private atomById = new Map<string, AtomNode>();
  /** The padlock drawn on each locked atom. */
  private lockBadges = new Map<string, Sprite>();
  private bondsOf = new Map<number, BondNode[]>();
  private ringCentres = new Map<Link, Vector3>();
  /** The atoms bonded to each atom, by position in the atom list, as the drawing code wants them. */
  private neighbourMap = new Map<number, number[]>();
  /** A fingerprint of everything about the structure except where its atoms are. */
  private signature = "";
  /** The session the camera was last framed on, so a new session is framed and an edit to the same one is not. */
  private framedSession: string | null = null;
  private down: { x: number; y: number } | null = null;
  /** Where the right button was pressed, so that its release can tell a click from a pan. */
  private rightDown: ScreenPoint | null = null;
  /** The last click, so that a second one soon after and in the same place can be told for a double-click. */
  private lastClick: (ScreenPoint & { at: number }) | null = null;
  /** Whether a left-drag pans the view, and a right-drag orbits it. */
  private panning = false;
  /** The roll ring being turned, with the angle the pointer was last at round it, or null while it is not. */
  private rolling: { last: number } | null = null;
  /** When the ring was last pressed, so that a second press soon after can level the view. */
  private lastRingPress = 0;
  /** Whether moving an atom carries the hydrogens bonded to it. */
  private carryHydrogens = false;
  /** The grid being shown, or null while there is none. */
  private grid: LineSegments | null = null;
  private locked: ReadonlySet<string> = new Set();
  /** Whether pressing on an atom picks it up. */
  private moving = false;
  private constraint: Constraint = "free";
  /** Whether constraints, and the molecule's gizmo, follow the scene's axes or the molecule's. */
  private space: Space = "world";
  private pivot: Pivot = "anchor";
  private pivotPoint = new Vector3();
  /**
   * The point the view was last anchored on, in the molecule's own frame so that it stays on the same spot of the
   * molecule when the molecule is moved. Panning does not change it: only framing, a right-click and a double-click do.
   * The scene axes and the grid are centred here.
   */
  private anchor = new Vector3();
  /** What the molecule's gizmo does, or null while it is not shown. */
  private gizmoMode: GizmoMode | null = null;
  /** The pose last drawn, so that an unchanged one is not drawn again. */
  private drawnPose: Pose | null = null;
  private grab: Grab | null = null;
  /** What the session last said of its geometry, which the strain markers are drawn from. */
  private strainResult: GeometryResult | null = null;
  /** The strain markers, which sit in the molecule's group so that they move with it, or null while there are none. */
  private strainMarks: Group | null = null;
  private lengthMarks: Group | null = null;
  private torsionShown: TorsionView | null = null;
  private angleShown: AngleView | null = null;
  /** The atoms the gizmo is carrying, each with where it was picked up, or null while it is not carrying any. */
  private shifting: { node: AtomNode; original: Vector3 }[] | null = null;
  /** The last view a triad click snapped to, so that clicking the same letter again can flip to the far side. */
  private snapped: { axis: Vector3; sign: 1 | -1; orientation: Quaternion } | null = null;
  /** Whether to draw how a move would go. */
  private debug = false;
  private guide: Guide | null = null;
  private emphasis: Emphasised = { selected: NO_EMPHASIS, active: NO_EMPHASIS, hover: NO_EMPHASIS };
  /** Whether to leave what is selected undrawn, with no lengths or names for it or for what is hovered. Hover is still tinted. */
  private plain = false;

  /**
   * @param canvas the canvas to draw on; it is sized to its CSS box
   */
  constructor(canvas: HTMLCanvasElement) {
    this.stage = new Stage({
      canvas,
      sizedBy: canvas,
      onMove: () => {
        this.placeGuide();
      },
    });
    // scrolling goes towards what the pointer is over, which also moves the view, so a molecule can be brought closer
    // without a pan. It moves the point the view orbits, but not the anchor the molecule's handles sit on
    this.stage.controls.zoomToCursor = true;
    // off until the viewport says otherwise, so they do not flash on before it does
    this.axes.visible = false;
    this.triad.visible = true;
    this.stage.scene.add(this.content, this.axes, this.guideFrame);
    this.gizmo = new MoleculeGizmo(this.stage, this.content, {
      grab: (axis) => {
        // the handles that translate hold a move to an axis or plane just as the keys do, so the keys and buttons follow
        const held = CONSTRAINT_OF_HANDLE[axis];
        if (this.gizmoMode !== "rotate" && held) {
          this.listeners.forEach((listener) => listener.constraint?.(held));
        }
      },
      start: (from) => this.listeners.forEach((listener) => listener.moleculeDragStart?.(from)),
      change: (pose, done) => {
        this.refreshGuide();
        this.listeners.forEach((listener) => listener.moleculeDrag?.(pose, done));
      },
      cancel: (from) => {
        this.drawnPose = from;
        this.refreshGuide();
        this.listeners.forEach((listener) => listener.moleculeDragCancel?.(from));
      },
      shiftStart: () => this.startShift(),
      shift: (offset, done) => this.shiftAtoms(offset, done),
      shiftCancel: () => this.cancelShift(),
    });
    this.bondGizmo = new BondGizmo(this.stage, {
      change: (value, done) => this.bondChanged(value, done),
      cancel: () => {
        this.syncBondGizmo();
        this.listeners.forEach((listener) => listener.bondDragCancel?.());
      },
    });
    this.stage.addOverlay((renderer, camera, capturing) => {
      // the triad is always there to be read, but is only part of a picture of the molecule if the axes are
      if (!capturing || this.axes.visible) {
        this.triad.draw(renderer, camera);
      }
      // the ring is for turning the view, and does not belong in a picture of the molecule
      if (!capturing) {
        this.ring.draw(renderer);
      }
    });

    canvas.addEventListener("pointermove", this.onMove);
    canvas.addEventListener("pointerleave", this.onLeave);
    // captured, so that this hears the press before the orbit controls do and can keep them from turning the camera
    canvas.addEventListener("pointerdown", this.onTriad, { capture: true });
    canvas.addEventListener("pointerdown", this.onRing, { capture: true });
    canvas.addEventListener("pointerdown", this.onGrab, { capture: true });
    canvas.addEventListener("pointerdown", this.onDown);
    canvas.addEventListener("pointerup", this.onUp);
    canvas.addEventListener("pointerup", this.onRightClick);
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

    // a molecule in the user's hand is where the pointer has put it, and the session is only catching up
    if (!this.gizmo.dragging) {
      this.applyPose(state.transform);
    }
    if (signature === this.signature) {
      // the atom in the user's hand is where the pointer has put it, and the session is only catching up
      const held = new Set([
        ...(this.grab?.dragging ? this.grab.members.map((member) => member.node.atom.id) : []),
        ...(this.shifting?.map(({ node }) => node.atom.id) ?? []),
      ]);
      this.moveAtoms(state.atoms.filter((atom) => !held.has(atom.id)).map((atom) => [atom.id, atom.position]));
      this.refreshLocks();
      this.syncGizmo();
      this.syncBondGizmo();
      return;
    }

    this.grab = null;
    this.stage.controls.enabled = true;
    this.signature = signature;
    this.build(state);
    this.refreshLocks();
    if (this.framedSession !== state.session_id) {
      this.framedSession = state.session_id;
      this.frameScene();
    }
    this.syncGizmo();
    this.syncBondGizmo();
  }

  /**
   * Puts the molecule where its pose says. No atom moves: the pose carries the group they are drawn in.
   *
   * @param pose where the molecule sits in the scene
   */
  private applyPose(pose: Pose): void {
    const drawn = this.drawnPose;
    if (
      drawn &&
      drawn.position.x === pose.position.x &&
      drawn.position.y === pose.position.y &&
      drawn.position.z === pose.position.z &&
      drawn.rotation.x === pose.rotation.x &&
      drawn.rotation.y === pose.rotation.y &&
      drawn.rotation.z === pose.rotation.z &&
      drawn.rotation.w === pose.rotation.w
    ) {
      return;
    }

    this.drawnPose = pose;
    this.content.position.set(pose.position.x, pose.position.y, pose.position.z);
    this.content.quaternion.set(pose.rotation.x, pose.rotation.y, pose.rotation.z, pose.rotation.w);
    this.content.updateMatrixWorld(true);
    this.placeReferences();
    this.placeGuide();
    this.stage.requestRender();
  }

  /**
   * Shows what the molecule's gizmo does, or hides it.
   *
   * @param mode translate to carry the molecule along an axis or plane, rotate to turn it about the pivot, atoms to carry
   *     the selected atoms within it, or null for none of them
   */
  setGizmo(mode: GizmoMode | null): void {
    this.gizmoMode = mode;
    this.syncGizmo();
  }

  /**
   * Chooses whether constraints, and the molecule's gizmo, follow the scene's axes or the molecule's own.
   *
   * @param space which to follow
   */
  setSpace(space: Space): void {
    this.space = space;
    this.placeGrid();
    this.syncGizmo();
    this.refreshGuide();
  }

  /**
   * Chooses the point the molecule's gizmo sits on and the molecule turns about.
   *
   * @param pivot the point the view orbits, the middle of its atoms, its own origin, or a point
   * @param point where the point is, in the scene, when the pivot is a point
   */
  setPivot(pivot: Pivot, point: Vec3): void {
    this.pivot = pivot;
    this.pivotPoint.set(point.x, point.y, point.z);
    this.syncGizmo();
  }

  /**
   * Offers a handle on a bond, or takes it away: an arrow along the bond that slides to change its length, or a ring
   * about it that turns to change its torsion. Dragging it reports through the listeners' `bondDrag`, and the session
   * moves the atoms.
   *
   * @param handle what the handle does and to which bond, or null for none
   */
  setBondHandle(handle: BondHandle | null): void {
    this.bondHandle = handle;
    this.syncBondGizmo();
  }

  /** Brings the handle to where what it changes is now, or takes it away. A handle being dragged is left alone. */
  private syncBondGizmo(): void {
    const handle = this.bondHandle;
    if (!handle) {
      this.bondGizmo.hide();
      return;
    }

    if (handle.mode === "angle") {
      const centre = this.atomById.get(handle.centre);
      const from = this.atomById.get(handle.from);
      const to = this.atomById.get(handle.to);
      if (!centre || !from || !to) {
        this.bondGizmo.hide();
        return;
      }

      // the ring is at the atom, and turns about the line at right angles to the plane of the two bonds
      const normal = planeNormal(minus(from.at, centre.at), minus(to.at, centre.at));
      const axis = new Vector3(normal.x, normal.y, normal.z).applyQuaternion(this.content.quaternion);
      this.bondGizmo.show("angle", this.worldOf(centre), axis, angleAt(from.at, centre.at, to.at), 1);
      return;
    }

    const link = this.bonds.find((bond) => bond.link.bond.id === handle.bond)?.link;
    const first = link && this.atomById.get(link.bond.first);
    const second = link && this.atomById.get(link.bond.second);
    if (!link || !first || !second) {
      this.bondGizmo.hide();
      return;
    }

    const a = this.worldOf(first);
    const b = this.worldOf(second);
    if (handle.mode === "length") {
      // the arrow sits on the atom that moves, which for neither held is the second
      const onFirst = handle.hold === "second";
      this.bondGizmo.show("length", onFirst ? a : b, b.clone().sub(a), a.distanceTo(b), onFirst ? -1 : 1);
      return;
    }

    const from = this.atomById.get(handle.from);
    const to = this.atomById.get(handle.to);
    if (!from || !to) {
      this.bondGizmo.hide();
      return;
    }
    this.bondGizmo.show("turn", a.clone().add(b).multiplyScalar(0.5), b.clone().sub(a), torsion(from.at, first.at, second.at, to.at), 1);
  }

  /**
   * Tells the listeners where the handle has got to.
   *
   * @param value the length, torsion or angle the handle has put what it changes at
   * @param done whether the handle has just been let go
   */
  private bondChanged(value: number, done: boolean): void {
    const handle = this.bondHandle;
    if (!handle) {
      return;
    }

    let edit: BondEdit;
    if (handle.mode === "angle") {
      const held = handle.hold === "first" ? handle.from : handle.hold === "second" ? handle.to : null;
      edit = { kind: "angle", centre: handle.centre, from: handle.from, to: handle.to, value, hold: held };
    } else {
      const link = this.bonds.find((bond) => bond.link.bond.id === handle.bond)?.link;
      if (!link) {
        return;
      }
      const held = handle.hold === "first" ? link.bond.first : handle.hold === "second" ? link.bond.second : null;
      edit =
        handle.mode === "length"
          ? { kind: "length", bond: handle.bond, value, hold: held }
          : { kind: "torsion", bond: handle.bond, from: handle.from, to: handle.to, value, hold: held };
    }
    this.listeners.forEach((listener) => listener.bondDrag?.(edit, done));
    if (done) {
      this.syncBondGizmo();
    }
  }

  /** Whether the pointer is over a handle of the molecule's or of a bond, so that pressing would take hold of it. */
  private get handleHovered(): boolean {
    return this.gizmo.hovered || this.bondGizmo.hovered;
  }

  /** Brings the gizmo into line with its mode, space, pivot and the molecule's pose. A gizmo being dragged is left alone. */
  private syncGizmo(): void {
    if (this.gizmoMode === null || this.atoms.length === 0) {
      this.gizmo.hide();
      return;
    }

    if (this.gizmoMode === "atoms") {
      // a selection with a locked atom in it cannot be moved, so there is nothing to put handles on
      const nodes = this.selectedNodes();
      if (nodes.length === 0 || this.selectionLocked()) {
        this.gizmo.hide();
        return;
      }

      const box = new Box3();
      nodes.forEach((node) => box.expandByPoint(this.worldOf(node)));
      this.gizmo.show("atoms", this.space, box.getCenter(new Vector3()));
      return;
    }

    this.gizmo.show(this.gizmoMode, this.space, this.pivotWorld());
  }

  /** The drawn atoms that are selected. */
  private selectedNodes(): AtomNode[] {
    return [...this.emphasis.selected.atoms].flatMap((id) => this.atomById.get(id) ?? []);
  }

  /**
   * Adds the hydrogens that go with some atoms when they are moved, if that is on.
   *
   * @param ids the atoms to move
   * @returns them, and, when carrying hydrogens, the terminal hydrogens bonded to them
   */
  private withCarried(ids: Iterable<string>): string[] {
    if (!this.carryHydrogens) {
      return [...ids];
    }

    return withTerminalHydrogens(ids, (id) => {
      const node = this.atomById.get(id);
      return node && { id, element: node.atom.element, neighbours: node.neighbours.flatMap((at) => this.atoms[at]?.atom.id ?? []) };
    });
  }

  /** A handle of the gizmo has been taken hold of in the atoms mode: the selected atoms are picked up. */
  private startShift(): void {
    const nodes = this.withCarried(this.emphasis.selected.atoms).flatMap((id) => this.atomById.get(id) ?? []);
    if (nodes.length === 0) {
      return;
    }

    this.shifting = nodes.map((node) => ({ node, original: node.at.clone() }));
    const [active] = this.emphasis.active.atoms;
    this.listeners.forEach((listener) => listener.dragStart?.(active ?? nodes[0]!.atom.id));
  }

  /**
   * Carries the picked-up atoms.
   *
   * @param offset how far from where they were picked up, in the molecule's own frame
   * @param done whether the handle has just been let go
   */
  private shiftAtoms(offset: Vector3, done: boolean): void {
    const carried = this.shifting;
    if (!carried) {
      return;
    }

    const moves = carried.map(({ node, original }): AtomMove => ({ id: node.atom.id, position: toVec3(original.clone().add(offset)) }));
    this.moveAtoms(moves.map((move) => [move.id, move.position]));
    if (done) {
      this.shifting = null;
    }
    this.listeners.forEach((listener) => listener.drag?.(moves, done));
  }

  /** Puts the picked-up atoms back where they were. */
  private cancelShift(): void {
    const carried = this.shifting;
    this.shifting = null;
    if (!carried) {
      return;
    }

    const moves = carried.map(({ node, original }): AtomMove => ({ id: node.atom.id, position: toVec3(original) }));
    this.moveAtoms(moves.map((move) => [move.id, move.position]));
    this.listeners.forEach((listener) => listener.dragCancel?.(moves));
  }

  /** Where the pivot is in the scene. */
  private pivotWorld(): Vector3 {
    if (this.pivot === "point") {
      return this.pivotPoint.clone();
    }
    if (this.pivot === "anchor") {
      return this.content.localToWorld(this.anchor.clone());
    }
    if (this.pivot === "origin" || this.atoms.length === 0) {
      return this.content.position.clone();
    }

    const box = new Box3();
    this.atoms.forEach((node) => box.expandByPoint(this.worldOf(node)));
    return box.getCenter(new Vector3());
  }

  /** Where an atom is in the scene, which is not where it is in the molecule's own frame once the molecule has moved. */
  private worldOf(node: AtomNode): Vector3 {
    return this.content.localToWorld(node.at.clone());
  }

  /**
   * Turns a ray in the scene into the same ray in the molecule's own frame, which is where atoms are placed.
   *
   * @param ray a ray in the scene
   * @returns the ray in the molecule's frame
   */
  private localRay(ray: Ray): Ray {
    const inverse = this.content.quaternion.clone().invert();
    return new Ray(this.content.worldToLocal(ray.origin.clone()), ray.direction.clone().applyQuaternion(inverse));
  }

  /**
   * @returns the directions of the axes a constraint names, in the molecule's own frame, which are its own axes in
   *     local space and the scene's, turned back by the molecule's rotation, in world space
   */
  private dragBasis(): Basis {
    if (this.space === "local") {
      return STANDARD_BASIS;
    }

    const inverse = this.content.quaternion.clone().invert();
    return {
      x: STANDARD_BASIS.x.clone().applyQuaternion(inverse),
      y: STANDARD_BASIS.y.clone().applyQuaternion(inverse),
      z: STANDARD_BASIS.z.clone().applyQuaternion(inverse),
    };
  }

  /** Draws a padlock on each locked atom and takes it off the rest. */
  private refreshLocks(): void {
    for (const [id, badge] of this.lockBadges) {
      if (!this.locked.has(id) || !this.atomById.has(id)) {
        this.content.remove(badge);
        badge.material.dispose();
        this.lockBadges.delete(id);
      }
    }

    for (const id of this.locked) {
      const node = this.atomById.get(id);
      if (node && !this.lockBadges.has(id)) {
        const badge = this.kit.labels.lockBadge(this.content, node.at, ballRadius(node.atom.radius, STYLE, true));
        this.content.add(badge);
        this.lockBadges.set(id, badge);
      }
    }
    this.refreshGuide();
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
    this.drawLengths();
    this.syncBondGizmo();
    this.placeGuide();
    // the handles sit on the selected atoms, so they follow when something else moves them
    if (this.gizmoMode === "atoms") {
      this.syncGizmo();
    }
  }

  /**
   * Tints what is selected, active and hovered. Hover shows over active, and active over selected.
   *
   * @param emphasis what to tint; the tints are kept across redraws as atoms move
   */
  setEmphasis(emphasis: Emphasised): void {
    this.emphasis = emphasis;
    this.refreshEmphasis();
    this.drawLengths();
    this.refreshGuide();
    this.syncGizmo();
  }

  /**
   * Chooses whether what is selected is drawn.
   *
   * @param plain true to draw no tint for the selection, and no lengths or names for it or the hover
   */
  setPlain(plain: boolean): void {
    if (plain !== this.plain) {
      this.plain = plain;
      this.refreshEmphasis();
      this.drawLengths();
    }
  }

  /**
   * Marks where the geometry is strained.
   *
   * @param result what the session said of its geometry, or null to show nothing
   */
  setStrain(result: GeometryResult | null): void {
    this.strainResult = result;
    this.drawStrain();
  }

  /**
   * Marks a torsion: the two bonds to the atoms it is measured from, their names, and its angle at the middle of the
   * bond it is about.
   *
   * @param torsion the torsion to mark, or null for none
   */
  setTorsion(torsion: TorsionView | null): void {
    this.torsionShown = torsion;
    this.drawLengths();
  }

  /**
   * Marks an angle: the two bonds, the names of the atoms at their ends, and its size inside it. With debugging on it also
   * draws the plane the angle opens and closes in.
   *
   * @param angle the angle to mark, or null for none
   */
  setAngle(angle: AngleView | null): void {
    this.angleShown = angle;
    this.drawLengths();
  }

  /**
   * Labels the length of each bond that is selected, and of each bond on a few selected atoms, and names the two atoms of
   * each bond that is hovered or selected, replacing what was drawn. A selection of many atoms is not labelled, because
   * the labels would hide what they measure.
   */
  private drawLengths(): void {
    if (this.lengthMarks) {
      this.content.remove(this.lengthMarks);
      disposeMaterials(this.lengthMarks);
      this.lengthMarks = null;
    }

    const { selected, hover } = this.plain ? { selected: NO_EMPHASIS, hover: NO_EMPHASIS } : this.emphasis;
    const few = selected.atoms.size <= LENGTH_LABEL_ATOMS;
    const measured = this.bonds.filter(({ link }) => selected.bonds.has(link.bond.id) || (few && (selected.atoms.has(link.bond.first) || selected.atoms.has(link.bond.second))));
    const named = this.bonds.filter(({ link }) => selected.bonds.has(link.bond.id) || hover.bonds.has(link.bond.id));

    const marks = new Group();
    if (measured.length <= LENGTH_LABEL_BONDS) {
      for (const { link } of measured) {
        const first = this.atomById.get(link.bond.first);
        const second = this.atomById.get(link.bond.second);
        if (first && second) {
          const note = this.kit.labels.label(`${first.at.distanceTo(second.at).toFixed(3)} Å`, "#1f3d99", 0.22);
          note.position.copy(first.at).add(second.at).multiplyScalar(0.5);
          marks.add(note);
        }
      }
    }

    // each atom is named once however many of the bonds on it are named
    const atomsNamed = new Set(named.flatMap(({ link }) => [link.bond.first, link.bond.second]));
    for (const id of atomsNamed) {
      const node = this.atomById.get(id);
      if (node) {
        // the name sits just above the atom, which is as far as the ball reaches plus a little
        const note = this.kit.labels.label(`${node.atom.element}${node.index + 1}`, "#222222", 0.26);
        note.position.copy(node.at);
        note.position.y += ballRadius(node.atom.radius, STYLE, true) + 0.25;
        marks.add(note);
      }
    }

    this.markTorsion(marks);
    this.markAngle(marks);

    if (marks.children.length > 0) {
      this.content.add(marks);
      this.lengthMarks = marks;
    }
    this.stage.requestRender();
  }

  /**
   * Adds the marks of the torsion being shown, if its atoms are all drawn.
   *
   * @param marks the group to add them to
   */
  private markTorsion(marks: Group): void {
    const shown = this.torsionShown;
    const bond = shown && this.bonds.find(({ link }) => link.bond.id === shown.bond)?.link.bond;
    const [a, b, c, d] = shown && bond ? [shown.first, bond.first, bond.second, shown.second].map((id) => this.atomById.get(id)) : [];
    if (!a || !b || !c || !d) {
      return;
    }

    const line = new MeshBasicMaterial({ color: ANGLE_COLOUR, depthTest: false });
    marks.add(this.kit.cylinder(a.at, b.at, 0.035, line));
    marks.add(this.kit.cylinder(c.at, d.at, 0.035, line.clone()));

    for (const node of [a, d]) {
      const name = this.kit.labels.label(`${node.atom.element}${node.index + 1}`, "#222222", 0.26);
      name.position.copy(node.at);
      name.position.y += ballRadius(node.atom.radius, STYLE, true) + 0.25;
      marks.add(name);
    }

    const angle = torsion(a.at, b.at, c.at, d.at);
    const note = this.kit.labels.label(`${angle.toFixed(1)}°`, "#b35900", 0.24);
    note.position.copy(b.at).add(c.at).multiplyScalar(0.5);
    note.position.y -= 0.3;
    marks.add(note);
  }

  /**
   * Adds the marks of the angle being shown, if its atoms are all drawn.
   *
   * @param marks the group to add them to
   */
  private markAngle(marks: Group): void {
    const shown = this.angleShown;
    const centre = shown && this.atomById.get(shown.centre);
    const from = shown && this.atomById.get(shown.from);
    const to = shown && this.atomById.get(shown.to);
    if (!centre || !from || !to) {
      return;
    }

    const line = new MeshBasicMaterial({ color: ANGLE_COLOUR, depthTest: false });
    marks.add(this.kit.cylinder(centre.at, from.at, 0.035, line));
    marks.add(this.kit.cylinder(centre.at, to.at, 0.035, line.clone()));

    for (const node of [from, to]) {
      const name = this.kit.labels.label(`${node.atom.element}${node.index + 1}`, "#222222", 0.26);
      name.position.copy(node.at);
      name.position.y += ballRadius(node.atom.radius, STYLE, true) + 0.25;
      marks.add(name);
    }

    // the size is written inside the angle, on the line that bisects it
    const inside = from.at.clone().sub(centre.at).normalize().add(to.at.clone().sub(centre.at).normalize());
    const note = this.kit.labels.label(`${angleAt(from.at, centre.at, to.at).toFixed(1)}°`, "#b35900", 0.24);
    note.position.copy(centre.at).addScaledVector(inside.lengthSq() > 1e-9 ? inside.normalize() : new Vector3(0, 1, 0), 0.6);
    marks.add(note);

    if (this.debug) {
      marks.add(this.anglePlane(centre.at, from.at, to.at));
    }
  }

  /**
   * Makes the plane an angle opens and closes in: a faint sheet through the atom the angle is at and the ends of its two
   * bonds, large enough to be seen past them.
   *
   * @param centre where the angle is
   * @param from the end of one bond
   * @param to the end of the other
   * @returns the sheet, in the molecule's own frame
   */
  private anglePlane(centre: Vector3, from: Vector3, to: Vector3): Mesh {
    const normal = planeNormal(from.clone().sub(centre), to.clone().sub(centre));
    const size = 2 * Math.max(from.distanceTo(centre), to.distanceTo(centre)) + 1;
    const sheet = new Mesh(
      new PlaneGeometry(size, size),
      new MeshBasicMaterial({ color: "#7a3fff", transparent: true, opacity: 0.16, side: DoubleSide, depthWrite: false }),
    );
    sheet.position.copy(centre);
    // a plane is made facing +z, which is turned to face along the normal
    sheet.quaternion.setFromUnitVectors(new Vector3(0, 0, 1), new Vector3(normal.x, normal.y, normal.z));
    sheet.renderOrder = 4;
    return sheet;
  }

  /** Draws the strain markers afresh from the report, replacing what was drawn. */
  private drawStrain(): void {
    if (this.strainMarks) {
      this.content.remove(this.strainMarks);
      disposeMaterials(this.strainMarks);
      this.strainMarks = null;
    }

    const result = this.strainResult;
    if (result && result.status === "complete" && this.atoms.length > 0) {
      const marks = new Group();
      for (const { id, strain } of result.atom_strain) {
        const node = this.atomById.get(id);
        if (!node) {
          continue;
        }
        // a share is in tolerances, and six is as strained as it is worth telling apart: a faint amber halo that grows,
        // brightens and turns solid red as it gets there
        const amount = Math.min(1, strain / STRAIN_SATURATED);
        const halo = new Mesh(
          this.kit.sphereGeometry,
          new MeshBasicMaterial({
            color: MILD_STRAIN_COLOUR.clone().lerp(new Color(STRAIN_COLOUR), amount),
            transparent: true,
            opacity: 0.25 + 0.65 * amount,
            depthWrite: false,
          }),
        );
        halo.position.copy(node.at);
        halo.scale.setScalar(ballRadius(node.atom.radius, STYLE, true) * (1.25 + 0.4 * amount));
        halo.renderOrder = 5;
        marks.add(halo);
      }
      for (const clash of result.clashes) {
        const first = this.atomById.get(clash.first);
        const second = this.atomById.get(clash.second);
        if (first && second) {
          marks.add(this.kit.cylinder(first.at, second.at, 0.03, new MeshBasicMaterial({ color: STRAIN_COLOUR, depthTest: false })));
        }
      }

      const line = (a: Vector3, b: Vector3, colour: string, width: number) =>
        marks.add(this.kit.cylinder(a, b, width, new MeshBasicMaterial({ color: colour, depthTest: false })));
      for (const finding of result.findings) {
        const nodes = finding.atoms.map((id) => this.atomById.get(id));
        if (nodes.some((node) => !node)) {
          continue;
        }
        const [first, middle, last] = nodes as AtomNode[];

        if (finding.kind === "bond" && first && middle) {
          // the bond itself, with how long it is
          line(first.at, middle.at, STRAIN_COLOUR, 0.05);
          const note = this.kit.labels.label(`${finding.actual.toFixed(3)} Å`, "#c00000", 0.22);
          note.position.copy(first.at.clone().add(middle.at).multiplyScalar(0.5));
          marks.add(note);
        } else if (finding.kind === "angle" && first && middle && last) {
          // the two bonds that make the angle, with how wide it is at the atom between them
          line(middle.at, first.at, ANGLE_COLOUR, 0.035);
          line(middle.at, last.at, ANGLE_COLOUR, 0.035);
          const inside = first.at.clone().sub(middle.at).normalize().add(last.at.clone().sub(middle.at).normalize());
          const note = this.kit.labels.label(`${finding.actual.toFixed(1)}°`, "#b35900", 0.22);
          note.position.copy(middle.at).addScaledVector(inside.lengthSq() > 1e-9 ? inside.normalize() : new Vector3(0, 1, 0), 0.6);
          marks.add(note);
        }
      }
      this.content.add(marks);
      this.strainMarks = marks;
    }
    this.stage.requestRender();
  }

  /**
   * Takes a picture of what the camera sees, with the markings and handles that are showing.
   *
   * @returns a PNG data URL
   */
  capture(): string {
    return this.stage.capture();
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
      this.anchorOn(new Vector3());
      return;
    }

    const box = new Box3();
    nodes.forEach((node) => box.expandByPoint(this.worldOf(node)));
    const centre = box.getCenter(new Vector3());
    let radius = 1;
    for (const node of nodes) {
      radius = Math.max(radius, this.worldOf(node).distanceTo(centre) + Math.max(ballRadius(node.atom.radius, STYLE, true), 0.3));
    }
    this.stage.frame(centre, radius + 0.5);
    this.anchorOn(centre);
  }

  /**
   * Anchors the view on an atom, as a right-click on it does, which is where the molecule's gizmo sits when its pivot
   * is the anchor.
   *
   * @param id the atom's identifier; one that is not drawn is ignored
   */
  anchorOnAtom(id: string): void {
    const node = this.atomById.get(id);
    if (node) {
      const world = this.worldOf(node);
      this.stage.pivotOn(world);
      this.anchorOn(world);
    }
  }

  /**
   * Records where the view has been anchored, which is where the molecule's gizmo sits when its pivot is the anchor.
   *
   * @param world the point the view now orbits, in the scene
   */
  private anchorOn(world: Vector3): void {
    this.anchor = this.content.worldToLocal(world.clone());
    this.placeReferences();
    this.syncGizmo();
  }

  /** Puts the axes and the grid where they belong, which is where the anchor and the molecule now are. */
  private placeReferences(): void {
    this.placeAxes();
    this.placeGrid();
  }

  /**
   * Shows a grid in one of the scene's planes, or in all three, or takes it away.
   * <p>
   * The grid is centred on the orbit anchor, where the axes are drawn. In **World** space its lines run along the scene's
   * axes; in **Local** space they turn with the molecule. A square grid lies in a plane; a polar one is nested spheres,
   * fills space and has no plane.
   *
   * @param kind the grid to show, or {@code null} for none
   * @param plane the plane a square grid lies in, or all three; a polar grid ignores it
   */
  setGrid(kind: GridKind | null, plane: GridPlaneChoice): void {
    if (this.grid) {
      this.stage.scene.remove(this.grid);
      this.grid.geometry.dispose();
      (this.grid.material as LineBasicMaterial).dispose();
      this.grid = null;
    }

    if (kind) {
      this.grid = buildGrid(kind, plane);
      this.stage.scene.add(this.grid);
      this.placeGrid();
    }
    this.stage.requestRender();
  }

  /** Moves the grid onto the anchor, and turns it with the molecule if the space is the molecule's own. */
  private placeGrid(): void {
    const grid = this.grid;
    if (!grid) {
      return;
    }

    this.content.updateMatrixWorld(true);
    const centre = this.content.localToWorld(this.anchor.clone());
    grid.position.copy(centre);
    grid.quaternion.copy(this.space === "local" ? this.content.quaternion : new Quaternion());
    this.stage.requestRender();
  }

  /**
   * Puts the axes on the anchor, which is fixed to the molecule. They are not put on the point the camera orbits,
   * which a pan and a zoom towards the pointer both move along with the camera, so that they would sit in the middle of
   * the view however it was panned and make the molecule look as if it were the thing moving.
   */
  private placeAxes(): void {
    this.content.updateMatrixWorld(true);
    this.axes.position.copy(this.content.localToWorld(this.anchor.clone()));
    this.stage.requestRender();
  }

  /**
   * Shows or hides the scene axes, the arrows at the orbit anchor. The small triad in the corner, inside the roll ring,
   * is always shown, so that the way the view is turned can always be read and the ring has something to go round, but
   * a saved picture only has it when the axes are shown.
   *
   * @param visible whether to show them
   */
  setAxes(visible: boolean): void {
    this.axes.visible = visible;
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
      this.stage.canvas.style.cursor = "";
    }
    this.refreshGuide();
  }

  /**
   * Makes moving atoms carry the hydrogens bonded to them, each by the same offset, or stops it. Only what is picked up
   * from now on is affected: a drag already begun keeps what it took.
   *
   * @param enabled whether to carry them
   */
  setCarryHydrogens(enabled: boolean): void {
    this.carryHydrogens = enabled;
    this.refreshGuide();
    this.syncGizmo();
  }

  /**
   * Makes a left-drag pan the view, with a right-drag orbiting it, or puts them back as they were: left orbits, right
   * pans. Touch follows the left button, so one finger pans and two still zoom.
   *
   * @param enabled whether a left-drag pans
   */
  setPanning(enabled: boolean): void {
    this.panning = enabled;
    const controls = this.stage.controls;
    controls.mouseButtons.LEFT = enabled ? MOUSE.PAN : MOUSE.ROTATE;
    controls.mouseButtons.RIGHT = enabled ? MOUSE.ROTATE : MOUSE.PAN;
    controls.touches.ONE = enabled ? TOUCH.PAN : TOUCH.ROTATE;
    this.stage.canvas.style.cursor = enabled ? "grab" : "";
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
    this.drawLengths();
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
      grab.point = pointOnConstraint(grab.ray, grab.members[0]!.start, constraint, grab.viewDirection, this.dragBasis()) ?? grab.point;
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
    canvas.removeEventListener("pointerdown", this.onRing, { capture: true });
    canvas.removeEventListener("pointerdown", this.onGrab, { capture: true });
    canvas.removeEventListener("pointerdown", this.onDown);
    canvas.removeEventListener("pointerup", this.onUp);
    canvas.removeEventListener("pointerup", this.onRightClick);
    canvas.removeEventListener("pointercancel", this.onCancel);
    window.removeEventListener("keydown", this.onKey, { capture: true });
    this.clear();
    this.gizmo.dispose();
    this.bondGizmo.dispose();
    this.guide?.dispose();
    this.triad.dispose();
    this.ring.dispose();
    disposeMaterials(this.axes);
    this.setGrid(null, "xz");
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

  /** Whether a locked atom is among the selected ones, which keeps the selection from being moved. */
  private selectionLocked(): boolean {
    return this.withCarried(this.emphasis.selected.atoms).some((id) => this.locked.has(id));
  }

  /**
   * The atoms a press on an atom would pick up: the whole selection if the atom is one of several selected, and
   * otherwise only the atom, with the hydrogens bonded to them if those are being carried.
   *
   * @param id the atom pressed on
   * @returns the identifiers of the atoms that would move
   */
  private groupOf(id: string): string[] {
    const selected = this.emphasis.selected.atoms;
    return this.withCarried(selected.has(id) && selected.size > 1 ? selected : [id]);
  }

  /** Draws the guide for the current constraint afresh, or removes it when there is nothing for it to show. */
  private refreshGuide(): void {
    if (this.guide) {
      this.guideFrame.remove(this.guide.group);
      this.guide.dispose();
      this.guide = null;
    }

    if (this.debug && this.moving && this.guideTarget() && !this.selectionLocked()) {
      this.guide = makeGuide(this.constraint);
      this.guideFrame.add(this.guide.group);
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

    // the guide is drawn in the frame the constraint is measured in: the camera's for free movement, otherwise the
    // molecule's own axes or the scene's
    this.guideFrame.position.copy(this.worldOf(target));
    if (this.guide.facesCamera) {
      this.guideFrame.quaternion.copy(this.stage.camera.quaternion);
    } else if (this.space === "local") {
      this.guideFrame.quaternion.copy(this.content.quaternion);
    } else {
      this.guideFrame.quaternion.identity();
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
    this.strainMarks = null;
    this.lengthMarks = null;
    this.registry.clear();
    this.atoms = [];
    this.bonds = [];
    this.lockBadges = new Map();
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
      const tint = among(hover) ? HOVER_TINT : this.plain ? null : among(active) ? ACTIVE_TINT : among(selected) ? SELECTED_TINT : null;
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

  /** The pointer's ray in the molecule's own frame, which is the one atoms are dragged in. */
  private localRayOf(event: PointerEvent): Ray {
    return this.localRay(this.rayOf(event));
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
    const point = pointOnConstraint(ray, lead.start, this.constraint, grab.viewDirection, this.dragBasis());
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

  /**
   * A press on the roll ring takes hold of it, and turning it rolls the view by as far as the pointer goes round. A
   * second press soon after, in the same place, takes the roll out instead.
   */
  private readonly onRing = (event: PointerEvent): void => {
    const box = this.stage.canvas.getBoundingClientRect();
    if (event.button !== 0 || !this.ring.hit(box, event.clientX, event.clientY)) {
      return;
    }

    // the press is the ring's: it must not orbit the camera, pick up an atom behind it, or clear the selection
    event.stopImmediatePropagation();
    event.preventDefault();

    const now = performance.now();
    if (now - this.lastRingPress < DOUBLE_CLICK_MS) {
      this.lastRingPress = 0;
      this.stage.level();
      return;
    }
    this.lastRingPress = now;

    this.stage.controls.enabled = false;
    this.stage.canvas.setPointerCapture(event.pointerId);
    this.stage.canvas.style.cursor = "grabbing";
    this.rolling = { last: this.ring.angleAt(box, event.clientX, event.clientY) };
    this.ring.active = true;
    this.stage.requestRender();
  };

  /** Lets go of the roll ring, giving the camera its controls back. */
  private endRoll(): void {
    this.rolling = null;
    this.ring.active = false;
    this.stage.controls.enabled = true;
    this.stage.canvas.style.cursor = this.panning ? "grab" : "";
    this.stage.requestRender();
  }

  private readonly onGrab = (event: PointerEvent): void => {
    if (event.button === 0 && this.handleHovered) {
      // a handle is under the pointer, which takes hold of it, and the camera must stay put
      this.stage.controls.enabled = false;
      return;
    }

    if (!this.moving || event.button !== 0) {
      return;
    }

    const pick = this.pickAt(event);
    const node = pick?.kind === "atom" ? this.atomById.get(pick.id) : undefined;
    if (!pick || !node || this.locked.has(pick.id)) {
      return;
    }

    // an atom in a selection of several takes the rest with it; one outside the selection goes alone
    const group = this.groupOf(pick.id);
    const nodes = [node, ...group.filter((id) => id !== pick.id).map((id) => this.atomById.get(id))];
    if (nodes.some((each) => !each || this.locked.has(each.atom.id))) {
      return;
    }

    const ray = this.localRayOf(event);
    const viewDirection = this.stage.camera.getWorldDirection(new Vector3()).applyQuaternion(this.content.quaternion.clone().invert());
    const point = pointOnConstraint(ray, node.at, this.constraint, viewDirection, this.dragBasis());
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
    if (event.key === "Escape" && this.gizmo.dragging) {
      event.stopPropagation();
      this.gizmo.cancel();
    } else if (event.key === "Escape" && this.bondGizmo.dragging) {
      event.stopPropagation();
      this.bondGizmo.cancel();
    } else if (event.key === "Escape" && this.grab?.dragging) {
      // the key abandons the drag and goes no further, so it does not also clear the selection
      event.stopPropagation();
      this.cancelDrag();
    }
  };

  private readonly onMove = (event: PointerEvent): void => {
    const rolling = this.rolling;
    if (rolling && event.buttons !== 0) {
      // the picture turns as far as the pointer has gone round the ring since the last move, however far from it
      const angle = this.ring.angleAt(this.stage.canvas.getBoundingClientRect(), event.clientX, event.clientY);
      this.stage.roll(sweep(rolling.last, angle));
      rolling.last = angle;
      return;
    }

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

      grab.ray = this.localRayOf(event);
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
    const overRing = !overTriad && this.ring.hit(this.stage.canvas.getBoundingClientRect(), event.clientX, event.clientY);
    if (overRing !== this.ring.hovered) {
      this.ring.hovered = overRing;
      this.stage.requestRender();
    }
    const pick = this.pickAt(event);

    // an atom the move tool would refuse to pick up, because it or one moving with it is locked, says so
    const refused = this.moving && pick?.kind === "atom" && this.groupOf(pick.id).some((id) => this.locked.has(id));
    this.stage.canvas.style.cursor = overTriad ? "pointer" : overRing ? "grab" : refused ? "not-allowed" : this.panning ? "grab" : "";
    this.listeners.forEach((listener) => listener.hover?.(pick, { x: event.clientX, y: event.clientY }));
  };

  private readonly onLeave = (): void => {
    if (this.ring.hovered) {
      this.ring.hovered = false;
      this.stage.requestRender();
    }
    this.stage.canvas.style.cursor = this.panning ? "grab" : "";
    this.listeners.forEach((listener) => listener.hover?.(null, { x: 0, y: 0 }));
  };

  private readonly onDown = (event: PointerEvent): void => {
    // a press on a handle is the handle's, and when it lets go it must not also select what is under it
    this.down = event.button === 0 && !this.handleHovered ? { x: event.clientX, y: event.clientY } : null;
    this.rightDown = event.button === 2 ? { x: event.clientX, y: event.clientY } : null;
    if (this.panning && event.button === 0) {
      this.stage.canvas.style.cursor = "grabbing";
    }
  };

  /**
   * Re-binds the orbit anchor: a right-click that was not a pan or an orbit centres the view on the atom nearest what was
   * clicked, or goes back to the middle of the molecule if the click was on nothing.
   */
  private readonly onRightClick = (event: PointerEvent): void => {
    const down = this.rightDown;
    this.rightDown = null;
    if (event.button !== 2 || !down || Math.hypot(event.clientX - down.x, event.clientY - down.y) > CLICK_SLOP) {
      return;
    }

    this.centreOn(event);
  };

  /**
   * Brings the atom nearest where the pointer is to the middle of the view, which then orbits it and which the molecule's
   * handles sit on, keeping the view's angle and distance. Over nothing, it goes back to the middle of the molecule.
   *
   * @param event the pointer event that says where
   */
  private centreOn(event: PointerEvent): void {
    const hit = this.registry.cast(this.raycaster, this.stage.camera, this.stage.pointer(event))?.point;
    let nearest: Vector3 | null = null;
    let best = Infinity;
    if (hit) {
      for (const node of this.atoms) {
        const at = this.worldOf(node);
        const distance = at.distanceToSquared(hit);
        if (distance < best) {
          best = distance;
          nearest = at;
        }
      }
    }

    const box = new Box3();
    this.atoms.forEach((node) => box.expandByPoint(this.worldOf(node)));
    const anchored = nearest?.clone() ?? (box.isEmpty() ? new Vector3() : box.getCenter(new Vector3()));
    this.stage.pivotOn(anchored);
    this.anchorOn(anchored);
  }

  private readonly onUp = (event: PointerEvent): void => {
    if (this.rolling) {
      this.endRoll();
      return;
    }

    if (this.panning && event.button === 0) {
      this.stage.canvas.style.cursor = "grab";
    }

    const grab = this.grab;
    if (grab) {
      this.releaseGrab();
      if (grab.dragging) {
        this.down = null;
        // a pointer that has left what the drag is held to leaves the atoms where they are
        const moves =
          this.placesOf(grab, this.localRayOf(event)) ??
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

    // the second click of a double-click centres the view and is not also a click, which on an atom the first one selected
    // would deselect again
    const now = performance.now();
    const last = this.lastClick;
    if (last && now - last.at < DOUBLE_CLICK_MS && Math.hypot(event.clientX - last.x, event.clientY - last.y) <= CLICK_SLOP) {
      this.lastClick = null;
      this.centreOn(event);
      return;
    }
    this.lastClick = { x: event.clientX, y: event.clientY, at: now };

    const pick = this.pickAt(event);
    this.listeners.forEach((listener) => listener.click?.(pick, event.shiftKey || event.ctrlKey || event.metaKey));
  };

  private readonly onCancel = (): void => {
    if (this.rolling) {
      this.endRoll();
    }
    this.down = null;
    this.cancelDrag();
  };
}

/** The constraint a translate handle holds a move to, by the name three.js gives the handle. */
const CONSTRAINT_OF_HANDLE: Record<string, Constraint | undefined> = {
  X: "x",
  Y: "y",
  Z: "z",
  XY: "xy",
  YZ: "yz",
  XZ: "xz",
  XYZ: "free",
};

function toVec3(point: Vector3): Vec3 {
  return { x: point.x, y: point.y, z: point.z };
}

/** Everything about a state that decides how it is drawn, other than the atoms' positions. */
function signatureOf(state: SessionState): string {
  const atoms = state.atoms.map((atom) => `${atom.id}:${atom.element}:${atom.charge}:${atom.colour}:${atom.radius}`);
  const bonds = state.bonds.map((bond) => `${bond.id}:${bond.first}:${bond.second}:${bond.order}`);
  return `${atoms.join(",")}|${bonds.join(",")}`;
}

/**
 * @param a a point
 * @param b another
 * @returns the vector from `b` to `a`
 */
function minus(a: Vector3, b: Vector3): Vec3 {
  return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z };
}
