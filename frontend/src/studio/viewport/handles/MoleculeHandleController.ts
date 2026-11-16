import {Box3, Vector3} from "three";
import type {Vec3} from "../../../api/types/structure";
import type {Stage} from "../../../render/stage";
import type {AtomMove, Pose} from "../../session/messages";
import type {Pivot, Space} from "../../selection/ToolModel";
import type {Constraint} from "./dragPlane";
import type {CameraRig} from "../camera/CameraRig";
import type {CameraLock} from "../camera/CameraLock";
import type {ReferenceLayer} from "../scene/ReferenceLayer";
import type {AtomNode, MoleculeScene} from "../scene/MoleculeScene";
import type {StudioViewListener} from "../StudioViewListener";
import {toVec3} from "../vec";
import {type MoleculeHandleMode, MoleculeHandles} from "./MoleculeHandles";

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

export interface MoleculeHandleHost {
  stage: Stage;
  scene: MoleculeScene;
  rig: CameraRig;
  /** Keeps the camera's own controls off while a handle is held. */
  lock: CameraLock;
  reference: ReferenceLayer;
  /** Whether constraints, and the handles, follow the scene's axes or the molecule's. */
  space(): Space;
  /** The atoms to move along with some others. */
  carry(ids: Iterable<string>): string[];
  /** Whether the selection cannot be moved (e.g., locking.) */
  blocked(): boolean;
  /** Moves atoms in the scene. */
  moveAtoms(moves: Iterable<readonly [string, Vec3]>): void;
  /** Tells every listener something. */
  emit(tell: (listener: StudioViewListener) => void): void;
}

/** The handles on the molecule as a whole. */
export class MoleculeHandleController {
  private readonly handles: MoleculeHandles;
  /** What the handles do, or null while they are not shown. */
  private mode: MoleculeHandleMode | null = null;
  private pivot: Pivot = "anchor";
  private readonly pivotPoint = new Vector3();
  /** The pose last drawn, so that an unchanged one is not drawn again. */
  private drawnPose: Pose | null = null;
  /** The atoms the handles are carrying, each with where it was picked up, or null while they are not carrying any. */
  private shifting: { node: AtomNode; original: Vector3 }[] | null = null;

  /**
   * @param host the view around the handles
   */
  constructor(private readonly host: MoleculeHandleHost) {
    this.handles = new MoleculeHandles(host.stage, host.scene.content, host.lock, {
      grab: (axis) => {
        // the handles that translate hold a move to an axis or plane just as the keys do, so the keys and buttons follow
        const held = CONSTRAINT_OF_HANDLE[axis];
        if (this.mode !== "rotate" && held) {
          host.emit((listener) => listener.constraint?.(held));
        }
      },
      start: (from) => host.emit((listener) => listener.moleculeDragStart?.(from)),
      change: (pose, done) => {
        host.reference.refreshGuide();
        host.emit((listener) => listener.moleculeDrag?.(pose, done));
      },
      cancel: (from) => {
        this.drawnPose = from;
        host.reference.refreshGuide();
        host.emit((listener) => listener.moleculeDragCancel?.(from));
      },
      shiftStart: () => this.startShift(),
      shift: (offset, done) => this.shiftAtoms(offset, done),
      shiftCancel: () => this.cancelShift(),
    });
  }

  /** Whether the pointer is over a handle. */
  get hovered(): boolean {
    return this.handles.hovered;
  }

  /** Whether a handle is being dragged. */
  get dragging(): boolean {
    return this.handles.dragging;
  }

  /** The atoms a handle is carrying, which are where it has put them. */
  get shifted(): string[] {
    return this.shifting?.map(({ node }) => node.atom.id) ?? [];
  }

  /** Abandons a drag of a handle. */
  cancel(): void {
    this.handles.cancel();
  }

  /** Sets what the handles do. */
  setMode(mode: MoleculeHandleMode | null): void {
    this.mode = mode;
    this.sync();
  }

  /**
   * Chooses the point the handles sit on and the molecule turns about.
   *
   * @param pivot the point the view orbits, the middle of its atoms, its own origin, or a point
   * @param point where the point is, in the scene, when the pivot is a point
   */
  setPivot(pivot: Pivot, point: Vec3): void {
    this.pivot = pivot;
    this.pivotPoint.set(point.x, point.y, point.z);
    this.sync();
  }

  /**
   * Puts the molecule where its pose says.
   * No atom moves: the pose carries the group they are drawn in.
   *
   * @param pose where the molecule sits in the scene
   */
  applyPose(pose: Pose): void {
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

    const { scene, stage, reference } = this.host;
    const content = scene.content;
    this.drawnPose = pose;
    content.position.set(pose.position.x, pose.position.y, pose.position.z);
    content.quaternion.set(pose.rotation.x, pose.rotation.y, pose.rotation.z, pose.rotation.w);
    content.updateMatrixWorld(true);
    reference.placeReferences();
    reference.placeGuide();
    stage.requestRender();
  }

  /** Brings the handles into line with their mode, space, pivot and the molecule's pose. Handles being dragged are left alone. */
  sync(): void {
    const { scene } = this.host;
    if (this.mode === null || scene.atoms.length === 0) {
      this.handles.hide();
      return;
    }

    if (this.mode === "atoms") {
      // a selection with a locked atom in it cannot be moved, so there is nothing to put handles on
      const nodes = this.selectedNodes();
      if (nodes.length === 0 || this.host.blocked()) {
        this.handles.hide();
        return;
      }

      const box = new Box3();
      nodes.forEach((node) => box.expandByPoint(scene.worldOf(node)));
      this.handles.show("atoms", this.host.space(), box.getCenter(new Vector3()));
      return;
    }

    this.handles.show(this.mode, this.host.space(), this.pivotWorld());
  }

  /** Releases what the handles drew. */
  dispose(): void {
    this.handles.dispose();
  }

  /** The handles sit on the selected atoms in the atoms mode, so they follow when something else moves them. */
  followAtoms(): void {
    if (this.mode === "atoms") {
      this.sync();
    }
  }

  /** The drawn atoms that are selected. */
  private selectedNodes(): AtomNode[] {
    const { scene } = this.host;
    return [...scene.emphasis.selected.atoms].flatMap((id) => scene.atomById.get(id) ?? []);
  }

  /** Where the pivot is in the scene. */
  private pivotWorld(): Vector3 {
    const { scene, rig } = this.host;
    if (this.pivot === "point") {
      return this.pivotPoint.clone();
    }
    if (this.pivot === "anchor") {
      return scene.content.localToWorld(rig.anchor.clone());
    }
    if (this.pivot === "origin" || scene.atoms.length === 0) {
      return scene.content.position.clone();
    }

    const box = new Box3();
    scene.atoms.forEach((node) => box.expandByPoint(scene.worldOf(node)));
    return box.getCenter(new Vector3());
  }

  /** A handle has been taken hold of in the atoms mode: the selected atoms are picked up. */
  private startShift(): void {
    const { scene } = this.host;
    const nodes = this.host.carry(scene.emphasis.selected.atoms).flatMap((id) => scene.atomById.get(id) ?? []);
    if (nodes.length === 0) {
      return;
    }

    this.shifting = nodes.map((node) => ({ node, original: node.at.clone() }));
    const [active] = scene.emphasis.active.atoms;
    this.host.emit((listener) => listener.dragStart?.(active ?? nodes[0]!.atom.id));
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
    this.host.moveAtoms(moves.map((move) => [move.id, move.position]));
    if (done) {
      this.shifting = null;
    }
    this.host.emit((listener) => listener.drag?.(moves, done));
  }

  /** Puts the picked-up atoms back where they were. */
  private cancelShift(): void {
    const carried = this.shifting;
    this.shifting = null;
    if (!carried) {
      return;
    }

    const moves = carried.map(({ node, original }): AtomMove => ({ id: node.atom.id, position: toVec3(original) }));
    this.host.moveAtoms(moves.map((move) => [move.id, move.position]));
    this.host.emit((listener) => listener.dragCancel?.(moves));
  }
}
