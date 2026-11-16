import {type Group, Object3D, Quaternion, Vector3} from "three";
import {TransformControls} from "three/examples/jsm/controls/TransformControls.js";
import type {Pose} from "../../session/messages";
import type {Stage} from "../../../render/stage";
import type {CameraLock} from "../camera/CameraLock";
import type {Space} from "../../models/ToolModel";
import {type Constraint, constraintOfHandle} from "./dragPlane";

/**
 * What the handles do: carry the whole molecule, turn it, or carry the selected atoms within it, which changes
 * their coordinates and not the molecule's pose.
 */
export type MoleculeHandleMode = "translate" | "rotate" | "atoms";

export interface MoleculeHandleHooks {
  /** A handle has been taken hold of: `held` is what it holds a move to, or null if it does not translate, as a rotate handle does not. */
  grab(held: Constraint | null): void;
  /** A handle has been taken hold of, with the molecule at `from`. */
  start(from: Pose): void;
  /** The handle has put the molecule at `pose`. `done` is true for the last report, when the handle is let go. */
  change(pose: Pose, done: boolean): void;
  /** The drag was abandoned and the molecule put back at `from`. */
  cancel(from: Pose): void;
  /** A handle has been taken hold of in the atoms mode. */
  shiftStart(): void;
  /**
   * The handle has carried the atoms `offset` from where they were picked up, in the molecule's own frame.
   * `done` is true for the last report, when the handle is let go.
   */
  shift(offset: Vector3, done: boolean): void;
  /** A drag in the atoms mode was abandoned. */
  shiftCancel(): void;
  /** Lets the owner adjust where a translate handle puts the pivot.  */
  snap(pivot: Vector3, held: Constraint, inverted: boolean): Vector3 | null;
  /** A drag of a handle has ended, by being let go or abandoned, so anything shown for the snap can be taken away. */
  snapEnd(): void;
}

/** The handles for moving or turning a whole molecule. */
export class MoleculeHandles {
  private readonly controls: TransformControls;
  /** Stands where the pivot is, which the controls move and this reads. */
  private readonly proxy = new Object3D();
  private mode: MoleculeHandleMode | null = null;

  /** Where the molecule was, where the pivot was and how the handles were turned, when the handle was taken hold of. */
  private start: { position: Vector3; rotation: Quaternion; pivot: Vector3; handleRotation: Quaternion } | null = null;
  /** Whether Ctrl or Cmd was held at the pointer's last move. */
  private inverted = false;
  private readonly onPointer = (event: PointerEvent): void => {
    this.inverted = event.ctrlKey || event.metaKey;
  };

  /**
   * @param stage what the handles are drawn in and what they stop turning the camera of
   * @param molecule the group the molecule is drawn in, which this moves
   * @param lock keeps the camera's own controls off while a handle is held
   * @param hooks who to tell what happens
   */
  constructor(
    private readonly stage: Stage,
    private readonly molecule: Group,
    private readonly lock: CameraLock,
    private readonly hooks: MoleculeHandleHooks,
  ) {
    this.controls = new TransformControls(stage.camera, stage.canvas);
    this.controls.size = 0.9;

    stage.scene.add(this.proxy, this.controls.getHelper());

    this.controls.addEventListener("change", () => stage.requestRender());
    this.controls.addEventListener("dragging-changed", (event) => {
      if (event.value) {
        lock.hold("moleculeHandles");
      } else {
        lock.release("moleculeHandles");
      }
    });
    this.controls.addEventListener("mouseDown", () => {
      if (this.controls.axis !== null) {
        this.hooks.grab(constraintOfHandle(this.controls.axis));
      }
      this.start = {
        position: this.molecule.position.clone(),
        rotation: this.molecule.quaternion.clone(),
        pivot: this.proxy.position.clone(),
        handleRotation: this.proxy.quaternion.clone(),
      };
      if (this.mode === "atoms") {
        this.hooks.shiftStart();
      } else {
        this.hooks.start(this.poseOf(this.start.position, this.start.rotation));
      }
    });
    this.controls.addEventListener("objectChange", () => {
      this.snap();
      if (this.follow()) {
        this.report(false);
      }
    });
    this.controls.addEventListener("mouseUp", () => {
      this.hooks.snapEnd();
      if (this.follow()) {
        this.report(true);
        this.start = null;
      }
    });
    // in the capture phase, so the state is current before the controls act on the same event
    window.addEventListener("pointerdown", this.onPointer, true);
    window.addEventListener("pointermove", this.onPointer, true);
    window.addEventListener("pointerup", this.onPointer, true);
  }

  /** Whether the pointer is over a handle, so that pressing would take hold of it. */
  get hovered(): boolean {
    return this.mode !== null && this.controls.axis !== null;
  }

  /** Whether a handle is held. */
  get dragging(): boolean {
    return this.start !== null;
  }

  /**
   * Shows the handles for a mode, or moves them to a new pivot. Does nothing while a handle is held.
   *
   * @param mode what the handles do
   * @param space whether the handles follow the scene's axes or the molecule's
   * @param pivot where in the scene the handles sit and the molecule turns about
   * @param axis the direction in the scene of the one line to turn about, for the rotate mode, or null to offer every axis
   */
  show(mode: MoleculeHandleMode, space: Space, pivot: Vector3, axis: Vector3 | null = null): void {
    if (this.start) {
      return;
    }

    const about = mode === "rotate" ? axis : null;
    this.mode = mode;
    this.proxy.position.copy(pivot);
    if (about) {
      this.proxy.quaternion.setFromUnitVectors(new Vector3(1, 0, 0), about);
    } else {
      this.proxy.quaternion.copy(this.molecule.quaternion);
    }
    this.controls.setMode(mode === "atoms" ? "translate" : mode);
    // about a bond, the handles follow it: the x ring turns about the bond itself and the others about what is across it
    this.controls.setSpace(about ? "local" : space);
    this.controls.showX = true;
    this.controls.showY = true;
    this.controls.showZ = true;
    this.controls.attach(this.proxy);
    this.stage.requestRender();
  }

  /** Takes the handles away. A drag in progress is abandoned. */
  hide(): void {
    if (this.start) {
      this.cancel();
    }
    this.mode = null;
    this.controls.detach();
    this.stage.requestRender();
  }

  /** Abandons the drag in progress, if there is one, putting the molecule back where it was picked up. */
  cancel(): void {
    const start = this.start;
    if (!start) {
      return;
    }
    this.start = null;
    this.hooks.snapEnd();

    if (this.mode !== "atoms") {
      this.molecule.position.copy(start.position);
      this.molecule.quaternion.copy(start.rotation);
      this.molecule.updateMatrixWorld(true);
    }
    this.proxy.position.copy(start.pivot);
    this.proxy.quaternion.copy(start.handleRotation);

    // the controls go on thinking a handle is held until the button comes up, so they are let go of and taken up afresh
    this.controls.detach();
    if (this.mode) {
      this.controls.attach(this.proxy);
    }
    this.stage.requestRender();
    if (this.mode === "atoms") {
      this.hooks.shiftCancel();
    } else {
      this.hooks.cancel(this.poseOf(start.position, start.rotation));
    }
  }

  /** Releases the handles. */
  dispose(): void {
    window.removeEventListener("pointerdown", this.onPointer, true);
    window.removeEventListener("pointermove", this.onPointer, true);
    window.removeEventListener("pointerup", this.onPointer, true);
    this.lock.release("moleculeHandles");
    this.stage.scene.remove(this.proxy, this.controls.getHelper());
    this.controls.dispose();
  }

  /** Moves the proxy to the nearest grid point, if the hooks say that is wanted, which the translate handles are held to. */
  private snap(): void {
    const held = constraintOfHandle(this.controls.axis);
    if (!this.start || this.mode === "rotate" || held === null) {
      return;
    }

    const snapped = this.hooks.snap(this.proxy.position, held, this.inverted);
    if (snapped) {
      this.proxy.position.copy(snapped);
      this.stage.requestRender();
    }
  }

  /**
   * Tells the hooks where the handle has got to.
   *
   * @param done whether the handle has just been let go
   */
  private report(done: boolean): void {
    const start = this.start;
    if (!start) {
      return;
    }

    if (this.mode === "atoms") {
      // the proxy is in the scene, and the atoms are placed in the molecule's own frame
      const offset = this.proxy.position.clone().sub(start.pivot).applyQuaternion(this.molecule.quaternion.clone().invert());
      this.hooks.shift(offset, done);
    } else {
      this.hooks.change(this.current(), done);
    }
  }

  /**
   * Puts the molecule where the proxy now says: turned as the proxy is, about the pivot the proxy began on. In the
   * atoms mode the molecule stays where it is.
   *
   * @returns whether a handle is held, which is when there is anything to follow
   */
  private follow(): boolean {
    const start = this.start;
    if (!start) {
      return false;
    }
    if (this.mode === "atoms") {
      return true;
    }

    const turn = this.proxy.quaternion.clone().multiply(start.handleRotation.clone().invert());
    this.molecule.quaternion.copy(turn).multiply(start.rotation);
    this.molecule.position.copy(start.position).sub(start.pivot).applyQuaternion(turn).add(this.proxy.position);
    this.molecule.updateMatrixWorld(true);
    this.stage.requestRender();
    return true;
  }

  private current(): Pose {
    return this.poseOf(this.molecule.position, this.molecule.quaternion);
  }

  private poseOf(position: Vector3, rotation: Quaternion): Pose {
    return { position: { x: position.x, y: position.y, z: position.z }, rotation: { x: rotation.x, y: rotation.y, z: rotation.z, w: rotation.w } };
  }
}
