import {Box3, Vector3} from "three";
import type {Vec3} from "../../../api/types/structure";
import type {Stage} from "../../../render/stage";
import type {AtomMove, Pose} from "../../session/messages";
import type {Pivot, Space} from "../../models/ToolModel";
import type {CameraRig} from "../camera/CameraRig";
import type {CameraLock} from "../camera/CameraLock";
import type {ReferenceLayer} from "../scene/ReferenceLayer";
import type {AtomNode, MoleculeScene} from "../scene/MoleculeScene";
import type {StudioViewListener} from "../StudioViewListener";
import {toVec3} from "../vec";
import {type MoleculeHandleMode, MoleculeHandles} from "./MoleculeHandles";

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
  /** Whether snapping to the grid is switched on. */
  snapOn(): boolean;
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
  /** The atoms at the ends of the bond the molecule is turned about, or null while it is turned freely. */
  private axis: { first: string; second: string } | null = null;
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
      grab: (held) => {
        // the handles that translate hold a move to an axis or plane just as the keys do, so the keys and buttons follow
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
      snap: (pivot, held, inverted) => {
        if (host.snapOn() === inverted) {
          host.reference.showSnap(null);
          return null;
        }
        const snapped = host.reference.snapWorld(pivot, held);
        host.reference.showSnap(snapped);
        return snapped;
      },
      snapEnd: () => host.reference.showSnap(null),
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

    this.handles.show(this.mode, this.host.space(), this.pivotWorld(), this.axisWorld());
  }

  /**
   * Chooses the bond the molecule is turned about.
   *
   * @param bond the atoms at its ends, or null to turn the molecule freely about the pivot
   */
  setAxis(bond: { first: string; second: string } | null): void {
    this.axis = bond;
    this.sync();
  }

  /** The direction in the scene of the bond the molecule is turned about, or null while it is turned freely. */
  private axisWorld(): Vector3 | null {
    const ends = this.axisEnds();
    return ends ? ends[1].clone().sub(ends[0]).normalize() : null;
  }

  /** Where the two ends of the bond the molecule is turned about are in the scene, or null if there is none. */
  private axisEnds(): [Vector3, Vector3] | null {
    const { scene } = this.host;
    const first = this.axis && scene.atomById.get(this.axis.first);
    const second = this.axis && scene.atomById.get(this.axis.second);
    if (this.mode !== "rotate" || !first || !second) {
      return null;
    }
    const ends: [Vector3, Vector3] = [scene.worldOf(first), scene.worldOf(second)];
    return ends[0].distanceToSquared(ends[1]) > 1e-12 ? ends : null;
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

  /** Where the pivot is in the scene: the middle of the bond the molecule is turned about, if there is one. */
  private pivotWorld(): Vector3 {
    const { scene, rig } = this.host;
    const ends = this.axisEnds();
    if (ends) {
      return ends[0].clone().add(ends[1]).multiplyScalar(0.5);
    }
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
