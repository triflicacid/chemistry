import {Box3, MOUSE, Ray, Raycaster, TOUCH, Vector3} from "three";
import type {Vec3} from "../../api/types/structure";
import {Primitives} from "../../render/primitives";
import {sweep} from "../../render/roll";
import {FIELD_OF_VIEW, Stage} from "../../render/stage";
import {type Basis, type Constraint, dragged, pointOnConstraint, STANDARD_BASIS} from "./handles/dragPlane";
import {withTerminalHydrogens} from "./hydrogens";
import type {GridKind, GridPlaneChoice} from "./grid";
import {CameraHud} from "./hud/CameraHud";
import {HUD_CENTRE, HUD_MARGIN, HUD_SIZE} from "./hud/constants";
import {BALL_TRAVEL} from "./hud/panDial";
import {RollRing} from "./hud/rollRing";
import {Triad} from "./hud/triad";
import type {HudHandle} from "./hud/dial";
import {MeasurementHandles} from "./handles/MeasurementHandles";
import {type MoleculeHandleMode, MoleculeHandles} from "./handles/MoleculeHandles";
import {CameraRig, SAME_VIEW} from "./camera/CameraRig";
import {Annotations} from "./scene/Annotations";
import {type AtomNode, type Emphasis, MoleculeScene, structureKeyOf} from "./scene/MoleculeScene";
import {ReferenceLayer} from "./scene/ReferenceLayer";
import type {AtomMove, BondEdit, GeometryResult, Pose, SessionState} from "../session/messages";
import type {Hold} from "../selection/bondLength";
import type {AngleView, Pivot, Space, TorsionView} from "../selection/ToolModel";
import {torsion} from "../selection/torsion";
import {angleAt, planeNormal} from "../selection/angle";
import type {CameraEdit, CameraState} from "../selection/CameraModel";
import type {PartItem} from "../selection/items";
import {type Binding, dispatchKey} from "../keys/binding";
import {KEYS} from "../keys/keymap";

/** Where the pointer is, in pixels. */
export interface ScreenPoint {
  x: number;
  y: number;
}

export interface StudioViewListener {
  /** The pointer moved over `pick`, or over nothing if it is null. Not called while a button is held. */
  hover?(pick: PartItem | null, at: ScreenPoint): void;
  /** A left click that was not a camera drag. `additive` is whether shift, control or command was held. */
  click?(pick: PartItem | null, additive: boolean): void;
  /** An atom has been picked up by the move tool and has begun to move, along with the rest of the selection if it is in it. */
  dragStart?(id: string): void;
  /** The dragged atoms are at `moves`' positions. `done` is true for the last report, when the pointer is released. */
  drag?(moves: AtomMove[], done: boolean): void;
  /** A drag was abandoned, and the atoms put back at `moves`' positions, where they were picked up. */
  dragCancel?(moves: AtomMove[]): void;
  /** A handle that translates has been taken hold of, so `constraint` is what the move is now held to. */
  constraint?(constraint: Constraint): void;
  /** A handle of the molecule's handles has been taken hold of, with the molecule at `from`. */
  moleculeDragStart?(from: Pose): void;
  /** The molecule handles have put the molecule at `pose`. `done` is true for the last report, when the handle is let go. */
  moleculeDrag?(pose: Pose, done: boolean): void;
  /** A drag of the molecule was abandoned, and it was put back at `from`, where it was picked up. */
  moleculeDragCancel?(from: Pose): void;
  /** A bond's handle has put the bond at `edit`. `done` is true for the last report, when the handle is let go. */
  bondDrag?(edit: BondEdit, done: boolean): void;
  /** A drag of a bond's handle was abandoned, and the session is to put the bond back. */
  bondDragCancel?(): void;
  /** The camera's state changed. `byUser` - whether a user change or initial load from server. */
  camera?(camera: CameraState, byUser: boolean): void;
}

/**
 * A handle on a bond or an atom, and which atom stays where it is: `first` is the bond's first atom, or the angle's `from`,
 * `second` its second atom or `to`, and `both` is neither.
 */
export type MeasurementTarget =
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

/** How far the pointer may move between pressing and releasing and still count as a click. */
const CLICK_SLOP = 4;

/** The longest, in milliseconds, between two clicks that make a double-click. */
const DOUBLE_CLICK_MS = 350;

/** The longest gap between two presses of the field-of-view slider that count as a double press, in milliseconds. */
const FOV_RESET_MS = 350;

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
  private readonly triad = new Triad(this.kit.labels, HUD_SIZE, HUD_MARGIN);
  /** The dial round the triad that rolls the view. */
  private readonly ring = new RollRing(HUD_CENTRE);
  private readonly cameraHud = new CameraHud();
  private readonly raycaster = new Raycaster();
  /** The molecule as drawn: its atoms and bonds, tints, locks and picking. */
  private readonly scene: MoleculeScene;
  /** What is marked on the molecule: strain, lengths, torsions and angles. */
  private readonly annotations: Annotations;
  /** The camera, and the anchor the view is held on. */
  private readonly rig: CameraRig;
  /** The scene's axes, the grid and the guide for a move. */
  private readonly reference: ReferenceLayer;
  private readonly moleculeHandles: MoleculeHandles;
  private readonly measurementHandles: MeasurementHandles;

  /** The handle on a bond that is being offered, or null for none. */
  private measurement: MeasurementTarget | null = null;
  private readonly listeners = new Set<StudioViewListener>();

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
  /** The camera sphere being dragged, and where the pointer was at the last step. */
  private hudDrag: { handle: HudHandle; x: number; y: number } | null = null;

  /** When the field-of-view slider was last pressed, from performance.now, or 0 if a press has just been used as a double press. */
  private lastFovPress = 0;
  /** When the ring was last pressed, so that a second press soon after can level the view. */
  private lastRingPress = 0;
  /** Whether moving an atom carries the hydrogens bonded to it. */
  private carryHydrogens = false;
  /** Whether pressing on an atom picks it up. */
  private moving = false;
  private constraint: Constraint = "free";
  /** Whether constraints, and the molecule's handles, follow the scene's axes or the molecule's. */
  private space: Space = "world";
  private pivot: Pivot = "anchor";
  private pivotPoint = new Vector3();
  /** What the molecule's handles do, or null while they are not shown. */
  private handleMode: MoleculeHandleMode | null = null;
  /** The pose last drawn, so that an unchanged one is not drawn again. */
  private drawnPose: Pose | null = null;
  private grab: Grab | null = null;
  /** The atoms the handles are carrying, each with where it was picked up, or null while they are not carrying any. */
  private shifting: { node: AtomNode; original: Vector3 }[] | null = null;

  /**
   * @param canvas the canvas to draw on; it is sized to its CSS box
   */
  constructor(canvas: HTMLCanvasElement) {
    this.stage = new Stage({
      canvas,
      sizedBy: canvas,
      onMove: () => {
        this.reference.placeGuide();
        const camera = this.rig.cameraState();
        const byUser = this.rig.byUser;
        this.listeners.forEach((listener) => listener.camera?.(camera, byUser));
      },
    });
    this.scene = new MoleculeScene(this.stage, this.kit);
    this.annotations = new Annotations(this.stage, this.kit, this.scene);
    this.rig = new CameraRig(this.stage, this.scene, () => {
      this.reference.placeReferences();
      this.syncMoleculeHandles();
    });
    this.reference = new ReferenceLayer(this.stage, this.scene, {
      space: () => this.space,
      constraint: () => this.constraint,
      anchor: () => this.rig.anchor,
      guideTarget: () => this.guideTarget(),
      guideWanted: () => this.annotations.debug && this.moving && this.guideTarget() !== null && !this.selectionLocked(),
    });
    // scrolling goes towards what the pointer is over, which also moves the view, so a molecule can be brought closer
    // without a pan. It moves the point the view orbits, but not the anchor the molecule's handles sit on
    this.stage.controls.zoomToCursor = true;
    // off until the viewport says otherwise, so they do not flash on before it does
    this.reference.axes.visible = false;
    this.triad.visible = true;
    this.stage.scene.add(this.scene.content, this.reference.axes, this.reference.guideFrame);
    this.moleculeHandles = new MoleculeHandles(this.stage, this.scene.content, {
      grab: (axis) => {
        // the handles that translate hold a move to an axis or plane just as the keys do, so the keys and buttons follow
        const held = CONSTRAINT_OF_HANDLE[axis];
        if (this.handleMode !== "rotate" && held) {
          this.listeners.forEach((listener) => listener.constraint?.(held));
        }
      },
      start: (from) => this.listeners.forEach((listener) => listener.moleculeDragStart?.(from)),
      change: (pose, done) => {
        this.reference.refreshGuide();
        this.listeners.forEach((listener) => listener.moleculeDrag?.(pose, done));
      },
      cancel: (from) => {
        this.drawnPose = from;
        this.reference.refreshGuide();
        this.listeners.forEach((listener) => listener.moleculeDragCancel?.(from));
      },
      shiftStart: () => this.startShift(),
      shift: (offset, done) => this.shiftAtoms(offset, done),
      shiftCancel: () => this.cancelShift(),
    });
    this.measurementHandles = new MeasurementHandles(this.stage, {
      change: (value, done) => this.bondChanged(value, done),
      cancel: () => {
        this.syncMeasurementHandles();
        this.listeners.forEach((listener) => listener.bondDragCancel?.());
      },
    });
    this.stage.addOverlay((renderer, camera, capturing) => {
      // the triad is always there to be read, but is only part of a picture of the molecule if the axes are
      if (!capturing || this.reference.axes.visible) {
        this.triad.draw(renderer, camera);
      }
      // the ring is for turning the view, and does not belong in a picture of the molecule
      if (!capturing) {
        this.ring.draw(renderer);
        this.cameraHud.draw(renderer, camera);
      }
    });

    canvas.addEventListener("pointermove", this.onMove);
    canvas.addEventListener("pointerleave", this.onLeave);
    // captured, so that this hears the press before the orbit controls do and can keep them from turning the camera
    canvas.addEventListener("pointerdown", this.onTriad, { capture: true });
    canvas.addEventListener("pointerdown", this.onRing, { capture: true });
    canvas.addEventListener("pointerdown", this.onHud, { capture: true });
    canvas.addEventListener("pointerdown", this.onGrab, { capture: true });
    canvas.addEventListener("pointerdown", this.onDown);
    canvas.addEventListener("pointerup", this.onUp);
    canvas.addEventListener("pointerup", this.onRightClick);
    canvas.addEventListener("pointercancel", this.onCancel);
    window.addEventListener("keydown", this.onKey, { capture: true });

    this.rig.frameScene();
  }

  /**
   * Shows a session's state.
   *
   * @param state the state to show; the camera is framed on it the first time a session is shown
   */
  setState(state: SessionState): void {
    const structureKey = structureKeyOf(state);
    this.scene.locked = new Set(state.locked);

    // a molecule in the user's hand is where the pointer has put it, and the session is only catching up
    if (!this.moleculeHandles.dragging) {
      this.applyPose(state.transform);
    }
    if (structureKey === this.scene.structureKey) {
      // the atom in the user's hand is where the pointer has put it, and the session is only catching up
      const held = new Set([
        ...(this.grab?.dragging ? this.grab.members.map((member) => member.node.atom.id) : []),
        ...(this.shifting?.map(({ node }) => node.atom.id) ?? []),
      ]);
      this.moveAtoms(state.atoms.filter((atom) => !held.has(atom.id)).map((atom) => [atom.id, atom.position]));
      this.scene.refreshLocks();
      this.reference.refreshGuide();
      this.syncMoleculeHandles();
      this.syncMeasurementHandles();
      return;
    }

    this.grab = null;
    this.stage.controls.enabled = true;
    this.scene.structureKey = structureKey;
    this.scene.clear();
    this.annotations.forget();
    this.scene.build(state);
    this.scene.refreshLocks();
    this.reference.refreshGuide();
    if (this.framedSession !== state.session_id) {
      this.framedSession = state.session_id;
      // a session that remembers where the camera was is shown from there, and one that does not is framed on its molecule
      this.rig.placeWhile(() => {
        this.rig.frameScene();
        if (state.camera) {
          this.rig.setCamera(state.camera);
        }
      });
    }
    this.syncMoleculeHandles();
    this.syncMeasurementHandles();
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

    const content = this.scene.content;
    this.drawnPose = pose;
    content.position.set(pose.position.x, pose.position.y, pose.position.z);
    content.quaternion.set(pose.rotation.x, pose.rotation.y, pose.rotation.z, pose.rotation.w);
    content.updateMatrixWorld(true);
    this.reference.placeReferences();
    this.reference.placeGuide();
    this.stage.requestRender();
  }

  /**
   * Shows what the molecule's handles do, or hides them.
   *
   * @param mode translate to carry the molecule along an axis or plane, rotate to turn it about the pivot, atoms to carry
   *     the selected atoms within it, or null for none of them
   */
  setHandleMode(mode: MoleculeHandleMode | null): void {
    this.handleMode = mode;
    this.syncMoleculeHandles();
  }

  /**
   * Chooses whether constraints, and the molecule's handles, follow the scene's axes or the molecule's own.
   *
   * @param space which to follow
   */
  setSpace(space: Space): void {
    this.space = space;
    this.reference.placeGrid();
    this.syncMoleculeHandles();
    this.reference.refreshGuide();
  }

  /**
   * Chooses the point the molecule's handles sit on and the molecule turns about.
   *
   * @param pivot the point the view orbits, the middle of its atoms, its own origin, or a point
   * @param point where the point is, in the scene, when the pivot is a point
   */
  setPivot(pivot: Pivot, point: Vec3): void {
    this.pivot = pivot;
    this.pivotPoint.set(point.x, point.y, point.z);
    this.syncMoleculeHandles();
  }

  /**
   * Offers a handle on a bond, or takes it away: an arrow along the bond that slides to change its length, or a ring
   * about it that turns to change its torsion. Dragging it reports through the listeners' `bondDrag`, and the session
   * moves the atoms.
   *
   * @param handle what the handle does and to which bond, or null for none
   */
  setMeasurement(handle: MeasurementTarget | null): void {
    this.measurement = handle;
    this.syncMeasurementHandles();
  }

  /** Brings the handle to where what it changes is now, or takes it away. A handle being dragged is left alone. */
  private syncMeasurementHandles(): void {
    const handle = this.measurement;
    if (!handle) {
      this.measurementHandles.hide();
      return;
    }

    if (handle.mode === "angle") {
      const centre = this.scene.atomById.get(handle.centre);
      const from = this.scene.atomById.get(handle.from);
      const to = this.scene.atomById.get(handle.to);
      if (!centre || !from || !to) {
        this.measurementHandles.hide();
        return;
      }

      // the ring is at the atom, and turns about the line at right angles to the plane of the two bonds
      const normal = planeNormal(minus(from.at, centre.at), minus(to.at, centre.at));
      const axis = new Vector3(normal.x, normal.y, normal.z).applyQuaternion(this.scene.content.quaternion);
      this.measurementHandles.show("angle", this.scene.worldOf(centre), axis, angleAt(from.at, centre.at, to.at), 1);
      return;
    }

    const link = this.scene.bonds.find((bond) => bond.link.bond.id === handle.bond)?.link;
    const first = link && this.scene.atomById.get(link.bond.first);
    const second = link && this.scene.atomById.get(link.bond.second);
    if (!link || !first || !second) {
      this.measurementHandles.hide();
      return;
    }

    const a = this.scene.worldOf(first);
    const b = this.scene.worldOf(second);
    if (handle.mode === "length") {
      // the arrow sits on the atom that moves, which for neither held is the second
      const onFirst = handle.hold === "second";
      this.measurementHandles.show("length", onFirst ? a : b, b.clone().sub(a), a.distanceTo(b), onFirst ? -1 : 1);
      return;
    }

    const from = this.scene.atomById.get(handle.from);
    const to = this.scene.atomById.get(handle.to);
    if (!from || !to) {
      this.measurementHandles.hide();
      return;
    }
    this.measurementHandles.show("turn", a.clone().add(b).multiplyScalar(0.5), b.clone().sub(a), torsion(from.at, first.at, second.at, to.at), 1);
  }

  /**
   * Tells the listeners where the handle has got to.
   *
   * @param value the length, torsion or angle the handle has put what it changes at
   * @param done whether the handle has just been let go
   */
  private bondChanged(value: number, done: boolean): void {
    const handle = this.measurement;
    if (!handle) {
      return;
    }

    let edit: BondEdit;
    if (handle.mode === "angle") {
      const held = handle.hold === "first" ? handle.from : handle.hold === "second" ? handle.to : null;
      edit = { kind: "angle", centre: handle.centre, from: handle.from, to: handle.to, value, hold: held };
    } else {
      const link = this.scene.bonds.find((bond) => bond.link.bond.id === handle.bond)?.link;
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
      this.syncMeasurementHandles();
    }
  }

  /** Whether the pointer is over a handle of the molecule's or of a bond, so that pressing would take hold of it. */
  private get handleHovered(): boolean {
    return this.moleculeHandles.hovered || this.measurementHandles.hovered;
  }

  /** Brings the handles into line with their mode, space, pivot and the molecule's pose. Handles being dragged are left alone. */
  private syncMoleculeHandles(): void {
    if (this.handleMode === null || this.scene.atoms.length === 0) {
      this.moleculeHandles.hide();
      return;
    }

    if (this.handleMode === "atoms") {
      // a selection with a locked atom in it cannot be moved, so there is nothing to put handles on
      const nodes = this.selectedNodes();
      if (nodes.length === 0 || this.selectionLocked()) {
        this.moleculeHandles.hide();
        return;
      }

      const box = new Box3();
      nodes.forEach((node) => box.expandByPoint(this.scene.worldOf(node)));
      this.moleculeHandles.show("atoms", this.space, box.getCenter(new Vector3()));
      return;
    }

    this.moleculeHandles.show(this.handleMode, this.space, this.pivotWorld());
  }

  /** The drawn atoms that are selected. */
  private selectedNodes(): AtomNode[] {
    return [...this.scene.emphasis.selected.atoms].flatMap((id) => this.scene.atomById.get(id) ?? []);
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
      const node = this.scene.atomById.get(id);
      return node && { id, element: node.atom.element, neighbours: node.neighbours.flatMap((at) => this.scene.atoms[at]?.atom.id ?? []) };
    });
  }

  /** A handle of the molecule's handles has been taken hold of in the atoms mode: the selected atoms are picked up. */
  private startShift(): void {
    const nodes = this.withCarried(this.scene.emphasis.selected.atoms).flatMap((id) => this.scene.atomById.get(id) ?? []);
    if (nodes.length === 0) {
      return;
    }

    this.shifting = nodes.map((node) => ({ node, original: node.at.clone() }));
    const [active] = this.scene.emphasis.active.atoms;
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
      return this.scene.content.localToWorld(this.rig.anchor.clone());
    }
    if (this.pivot === "origin" || this.scene.atoms.length === 0) {
      return this.scene.content.position.clone();
    }

    const box = new Box3();
    this.scene.atoms.forEach((node) => box.expandByPoint(this.scene.worldOf(node)));
    return box.getCenter(new Vector3());
  }

  /**
   * @returns the directions of the axes a constraint names, in the molecule's own frame, which are its own axes in
   *     local space and the scene's, turned back by the molecule's rotation, in world space
   */
  private dragBasis(): Basis {
    if (this.space === "local") {
      return STANDARD_BASIS;
    }

    const inverse = this.scene.content.quaternion.clone().invert();
    return {
      x: STANDARD_BASIS.x.clone().applyQuaternion(inverse),
      y: STANDARD_BASIS.y.clone().applyQuaternion(inverse),
      z: STANDARD_BASIS.z.clone().applyQuaternion(inverse),
    };
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
    if (!this.scene.moveAtoms(moves)) {
      return;
    }

    this.annotations.drawLengths();
    this.syncMeasurementHandles();
    this.reference.placeGuide();
    // the handles sit on the selected atoms, so they follow when something else moves them
    if (this.handleMode === "atoms") {
      this.syncMoleculeHandles();
    }
  }

  /**
   * Tints what is selected, active and hovered. Hover shows over active, and active over selected.
   *
   * @param emphasis what to tint; the tints are kept across redraws as atoms move
   */
  setEmphasis(emphasis: Emphasis): void {
    this.scene.emphasis = emphasis;
    this.scene.refreshEmphasis();
    this.annotations.drawLengths();
    this.reference.refreshGuide();
    this.syncMoleculeHandles();
  }

  /**
   * Chooses whether what is selected is drawn.
   *
   * @param plain true to draw no tint for the selection, and no lengths or names for it or the hover
   */
  setPlain(plain: boolean): void {
    if (plain !== this.scene.plain) {
      this.scene.plain = plain;
      this.scene.refreshEmphasis();
      this.annotations.drawLengths();
    }
  }

  /**
   * Marks where the geometry is strained.
   *
   * @param result what the session said of its geometry, or null to show nothing
   */
  setStrain(result: GeometryResult | null): void {
    this.annotations.setStrain(result);
  }

  /**
   * Marks a torsion: the two bonds to the atoms it is measured from, their names, and its angle at the middle of the
   * bond it is about.
   *
   * @param torsion the torsion to mark, or null for none
   */
  setTorsion(torsion: TorsionView | null): void {
    this.annotations.setTorsion(torsion);
  }

  /**
   * Marks an angle: the two bonds, the names of the atoms at their ends, and its size inside it. With debugging on it also
   * draws the plane the angle opens and closes in.
   *
   * @param angle the angle to mark, or null for none
   */
  setAngle(angle: AngleView | null): void {
    this.annotations.setAngle(angle);
  }

  /**
   * Takes a picture of what the camera sees, with the markings and handles that are showing.
   *
   * @returns a PNG data URL
   */
  capture(): string {
    return this.stage.capture();
  }

  /**
   * Says where the camera is.
   *
   * @returns its position, what it looks at, how it is turned about that, and its field of view
   */
  cameraState(): CameraState {
    return this.rig.cameraState();
  }

  /**
   * Moves the camera.
   *
   * @param edit what to change; one that would put the camera on what it looks at is ignored
   */
  setCamera(edit: CameraEdit): void {
    this.rig.setCamera(edit);
  }

  /**
   * Brings the middle of the molecule to the middle of the view, which then orbits it and which the molecule's handles
   * sit on, keeping the view's angle and distance.
   */
  centreCamera(): void {
    this.rig.centreCamera();
  }

  /**
   * Orbits the view as a drag on it would.
   *
   * @param dx how far the pointer went across, in pixels
   * @param dy how far it went down, in pixels
   */
  orbitBy(dx: number, dy: number): void {
    this.rig.orbitBy(dx, dy);
  }

  /**
   * Pans the view as a drag on it would.
   *
   * @param dx how far the pointer went across, in pixels
   * @param dy how far it went down, in pixels
   */
  panBy(dx: number, dy: number): void {
    this.rig.panBy(dx, dy);
  }

  /** Puts the camera at the defaults the code has: the default field of view, level, framing the whole molecule. */
  resetCamera(): void {
    this.rig.resetCamera();
  }

  /** Points the camera at the whole scene. */
  frameScene(): void {
    this.rig.frameScene();
  }

  /**
   * Points the camera at some atoms, orbiting their centre.
   *
   * @param ids the atoms to show; if none of them are drawn, the whole scene is framed
   */
  frameAtoms(ids: readonly string[]): void {
    this.rig.frameAtoms(ids);
  }

  /**
   * Anchors the view on an atom, as a right-click on it does, which is where the molecule's handles sit when its pivot
   * is the anchor.
   *
   * @param id the atom's identifier; one that is not drawn is ignored
   */
  anchorOnAtom(id: string): void {
    this.rig.anchorOnAtom(id);
  }

  /**
   * Shows a grid in one of the scene's planes, or in all three, or takes it away.
   *
   * @param kind the grid to show, or {@code null} for none
   * @param plane the plane a square grid lies in, or all three; a polar grid ignores it
   */
  setGrid(kind: GridKind | null, plane: GridPlaneChoice): void {
    this.reference.setGrid(kind, plane);
  }

  /**
   * Shows or hides the scene axes, the arrows at the orbit anchor. The small triad in the corner is always shown.
   *
   * @param visible whether to show them
   */
  setAxes(visible: boolean): void {
    this.reference.setAxes(visible);
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
    this.reference.refreshGuide();
  }

  /**
   * Makes moving atoms carry the hydrogens bonded to them, each by the same offset, or stops it. Only what is picked up
   * from now on is affected: a drag already begun keeps what it took.
   *
   * @param enabled whether to carry them
   */
  setCarryHydrogens(enabled: boolean): void {
    this.carryHydrogens = enabled;
    this.reference.refreshGuide();
    this.syncMoleculeHandles();
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
    // the spheres are the camera's controls, so they are there while the view is what is being worked on
    this.cameraHud.visible = enabled;
    if (!enabled) {
      this.cameraHud.hovered = null;
    }
    this.stage.requestRender();
  }

  /**
   * Turns the debugging overlays on or off. Today that is the guide for a move: arrows along the axis, or a faint
   * plane, that the atom being moved is held to.
   *
   * @param enabled whether to draw them
   */
  setDebug(enabled: boolean): void {
    this.annotations.debug = enabled;
    this.reference.refreshGuide();
    this.annotations.drawLengths();
  }

  /**
   * Holds a drag to an axis or plane, or lets it go. A drag already begun carries on from where the atom is now.
   *
   * @param constraint what a dragged atom is held to
   */
  setConstraint(constraint: Constraint): void {
    this.constraint = constraint;
    this.reference.refreshGuide();
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
    canvas.removeEventListener("pointerdown", this.onHud, { capture: true });
    canvas.removeEventListener("pointerdown", this.onGrab, { capture: true });
    canvas.removeEventListener("pointerdown", this.onDown);
    canvas.removeEventListener("pointerup", this.onUp);
    canvas.removeEventListener("pointerup", this.onRightClick);
    canvas.removeEventListener("pointercancel", this.onCancel);
    window.removeEventListener("keydown", this.onKey, { capture: true });
    this.scene.clear();
    this.moleculeHandles.dispose();
    this.measurementHandles.dispose();
    this.triad.dispose();
    this.ring.dispose();
    this.cameraHud.dispose();
    this.reference.dispose();
    this.kit.dispose();
    this.stage.dispose();
    this.listeners.clear();
  }

  /** The atom the move guide is drawn on: the one in hand, or else the active one. */
  private guideTarget(): AtomNode | null {
    if (this.grab?.dragging) {
      return this.grab.members[0]!.node;
    }
    const [active] = this.scene.emphasis.active.atoms;
    return active === undefined ? null : (this.scene.atomById.get(active) ?? null);
  }

  /** Whether a locked atom is among the selected ones, which keeps the selection from being moved. */
  private selectionLocked(): boolean {
    return this.withCarried(this.scene.emphasis.selected.atoms).some((id) => this.scene.locked.has(id));
  }

  /**
   * The atoms a press on an atom would pick up: the whole selection if the atom is one of several selected, and
   * otherwise only the atom, with the hydrogens bonded to them if those are being carried.
   *
   * @param id the atom pressed on
   * @returns the identifiers of the atoms that would move
   */
  private groupOf(id: string): string[] {
    const selected = this.scene.emphasis.selected.atoms;
    return this.withCarried(selected.has(id) && selected.size > 1 ? selected : [id]);
  }

  /** The pointer's ray into the scene. */
  private rayOf(event: PointerEvent): Ray {
    this.raycaster.setFromCamera(this.stage.pointer(event), this.stage.camera);
    return this.raycaster.ray.clone();
  }

  /** The pointer's ray in the molecule's own frame, which is the one atoms are dragged in. */
  private localRayOf(event: PointerEvent): Ray {
    return this.scene.localRay(this.rayOf(event));
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
    this.reference.refreshGuide();
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
    const last = this.rig.snapped;
    const again = last !== null && last.axis.dot(axis) > 0.99 && this.stage.camera.quaternion.angleTo(last.orientation) < SAME_VIEW;
    const sign = again && last.sign === 1 ? -1 : 1;

    this.stage.lookFrom(axis.clone().multiplyScalar(sign));
    this.rig.snapped = { axis, sign, orientation: this.stage.camera.quaternion.clone() };
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

  /**
   * A press on one of the camera spheres takes hold of it, and dragging orbits or pans the view by as far as the pointer
   * goes, along the way the handle holds it to.
   */
  private readonly onHud = (event: PointerEvent): void => {
    const handle = event.button === 0 ? this.cameraHud.hit(this.stage.canvas.getBoundingClientRect(), event.clientX, event.clientY) : null;
    if (!handle) {
      return;
    }

    // the press is the sphere's: it must not orbit the camera, pick up an atom behind it, or clear the selection
    event.stopImmediatePropagation();
    event.preventDefault();

    this.stage.controls.enabled = false;
    this.stage.canvas.setPointerCapture(event.pointerId);
    this.hudDrag = { handle, x: event.clientX, y: event.clientY };
    this.cameraHud.active = handle;
    this.stage.canvas.style.cursor = "grabbing";

    if (handle === "fov" || handle === "cone") {
      // pressing the slider or cone twice quickly puts the field of view back to the default
      const now = performance.now();
      const again = now - this.lastFovPress < FOV_RESET_MS;
      this.lastFovPress = again ? 0 : now;
      this.rig.setCamera({ fov: again ? FIELD_OF_VIEW : this.fovFromPointer(handle, event) });
    }
    this.stage.requestRender();
  };

  /** The field of view a pointer asks for on the slider, or by where it is about the cone. */
  private fovFromPointer(handle: HudHandle, event: PointerEvent): number {
    const canvas = this.stage.canvas.getBoundingClientRect();
    return handle === "cone" ? this.cameraHud.fovOfCone(canvas, event.clientX, event.clientY) : this.cameraHud.fovAt(canvas, event.clientX);
  }

  /** Moves the view by a step of a drag on a camera sphere. */
  private dragHud(event: PointerEvent, drag: { handle: HudHandle; x: number; y: number }): void {
    if (drag.handle === "fov" || drag.handle === "cone") {
      this.rig.setCamera({ fov: this.fovFromPointer(drag.handle, event) });
      this.stage.requestRender();
      return;
    }

    const across = drag.handle === "pitch" || drag.handle === "pan-y" ? 0 : event.clientX - drag.x;
    const down = drag.handle === "yaw" || drag.handle === "pan-x" ? 0 : event.clientY - drag.y;
    drag.x = event.clientX;
    drag.y = event.clientY;

    if (drag.handle === "orbit" || drag.handle === "yaw" || drag.handle === "pitch") {
      this.rig.orbitBy(across, down);
      return;
    }

    this.rig.panBy(across, down);
    // the ball is pulled out the way the view goes, as far as its track lets it
    const pulled = { x: this.cameraHud.ball.x + across, y: this.cameraHud.ball.y - down };
    const length = Math.hypot(pulled.x, pulled.y);
    const scale = length > BALL_TRAVEL ? BALL_TRAVEL / length : 1;
    this.cameraHud.ball = { x: pulled.x * scale, y: pulled.y * scale };
    this.stage.requestRender();
  }

  /** Lets go of a camera sphere, giving the camera its controls back and the pan ball back its place. */
  private endHudDrag(): void {
    this.hudDrag = null;
    this.cameraHud.active = null;
    this.cameraHud.ball = { x: 0, y: 0 };
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

    const pick = this.scene.pickAt(event);
    const node = pick?.kind === "atom" ? this.scene.atomById.get(pick.id) : undefined;
    if (!pick || !node || this.scene.locked.has(pick.id)) {
      return;
    }

    // an atom in a selection of several takes the rest with it; one outside the selection goes alone
    const group = this.groupOf(pick.id);
    const nodes = [node, ...group.filter((id) => id !== pick.id).map((id) => this.scene.atomById.get(id))];
    if (nodes.some((each) => !each || this.scene.locked.has(each.atom.id))) {
      return;
    }

    const ray = this.localRayOf(event);
    const viewDirection = this.stage.camera.getWorldDirection(new Vector3()).applyQuaternion(this.scene.content.quaternion.clone().invert());
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

  /** Shortcuts heard in the capture phase; see {@link KEYS} for why Escape is among them. */
  private readonly bindings: Binding[] = [
    {
      ...KEYS.cancelDrag,
      run: (event) => {
        if (this.moleculeHandles.dragging) {
          event.stopPropagation();
          this.moleculeHandles.cancel();
        } else if (this.measurementHandles.dragging) {
          event.stopPropagation();
          this.measurementHandles.cancel();
        } else if (this.grab?.dragging) {
          // the key abandons the drag and goes no further, so it does not also clear the selection
          event.stopPropagation();
          this.cancelDrag();
        }
      },
    },
  ];

  private readonly onKey = (event: KeyboardEvent): void => dispatchKey(this.bindings, event, true);

  private readonly onMove = (event: PointerEvent): void => {
    const sphereDrag = this.hudDrag;
    if (sphereDrag && event.buttons !== 0) {
      this.dragHud(event, sphereDrag);
      return;
    }

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
        this.reference.refreshGuide();
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
    const overSphere = this.cameraHud.hit(this.stage.canvas.getBoundingClientRect(), event.clientX, event.clientY);
    if (overSphere !== this.cameraHud.hovered) {
      this.cameraHud.hovered = overSphere;
      this.stage.requestRender();
    }
    const overRing = !overTriad && !overSphere && this.ring.hit(this.stage.canvas.getBoundingClientRect(), event.clientX, event.clientY);
    if (overRing !== this.ring.hovered) {
      this.ring.hovered = overRing;
      this.stage.requestRender();
    }
    const pick = this.scene.pickAt(event);

    // an atom the move tool would refuse to pick up, because it or one moving with it is locked, says so
    const refused = this.moving && pick?.kind === "atom" && this.groupOf(pick.id).some((id) => this.scene.locked.has(id));
    this.stage.canvas.style.cursor = overSphere ? HUD_CURSOR[overSphere] : overTriad ? "pointer" : overRing ? "grab" : refused ? "not-allowed" : this.panning ? "grab" : "";
    this.listeners.forEach((listener) => listener.hover?.(pick, { x: event.clientX, y: event.clientY }));
  };

  private readonly onLeave = (): void => {
    if (this.cameraHud.hovered) {
      this.cameraHud.hovered = null;
      this.stage.requestRender();
    }
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
    const hit = this.scene.hitPoint(event);
    let nearest: Vector3 | null = null;
    let best = Infinity;
    if (hit) {
      for (const node of this.scene.atoms) {
        const at = this.scene.worldOf(node);
        const distance = at.distanceToSquared(hit);
        if (distance < best) {
          best = distance;
          nearest = at;
        }
      }
    }

    const anchored = nearest?.clone() ?? this.scene.middleOfAtoms();
    this.stage.pivotOn(anchored);
    this.rig.anchorOn(anchored);
  }

  private readonly onUp = (event: PointerEvent): void => {
    if (this.hudDrag) {
      this.endHudDrag();
      return;
    }

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

    const pick = this.scene.pickAt(event);
    this.listeners.forEach((listener) => listener.click?.(pick, event.shiftKey || event.ctrlKey || event.metaKey));
  };

  private readonly onCancel = (): void => {
    if (this.hudDrag) {
      this.endHudDrag();
    }
    if (this.rolling) {
      this.endRoll();
    }
    this.down = null;
    this.cancelDrag();
  };
}

/** The cursor over each camera sphere handle: across or up and down for one that holds a drag to that way. */
const HUD_CURSOR: Record<HudHandle, string> = {
  orbit: "grab",
  yaw: "ew-resize",
  pitch: "ns-resize",
  pan: "grab",
  "pan-x": "ew-resize",
  "pan-y": "ns-resize",
  fov: "ew-resize",
  cone: "grab",
};

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

/**
 * @param a a point
 * @param b another
 * @returns the vector from `b` to `a`
 */
function minus(a: Vector3, b: Vector3): Vec3 {
  return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z };
}
