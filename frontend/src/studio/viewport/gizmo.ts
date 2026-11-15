import {type Group, Object3D, Quaternion, Vector3} from "three";
import {TransformControls} from "three/examples/jsm/controls/TransformControls.js";
import type {Pose} from "../session/messages";
import type {Stage} from "../../render/stage";
import type {Space} from "../selection/ToolModel";

/** What the gizmo does to the molecule. */
export type GizmoMode = "translate" | "rotate";

export interface GizmoHooks {
  /** A handle has been taken hold of, with the molecule at `from`. */
  start(from: Pose): void;
  /** The handle has put the molecule at `pose`. `done` is true for the last report, when the handle is let go. */
  change(pose: Pose, done: boolean): void;
  /** The drag was abandoned and the molecule put back at `from`. */
  cancel(from: Pose): void;
}

/** The handles for moving or turning a whole molecule. */
export class MoleculeGizmo {
  private readonly controls: TransformControls;
  /** Stands where the pivot is, which the controls move and this reads. */
  private readonly proxy = new Object3D();
  private mode: GizmoMode | null = null;

  /** Where the molecule was, and where the pivot was, when the handle was taken hold of. */
  private start: { position: Vector3; rotation: Quaternion; pivot: Vector3 } | null = null;

  /**
   * @param stage what the handles are drawn in and what they stop turning the camera of
   * @param molecule the group the molecule is drawn in, which this moves
   * @param hooks who to tell what happens
   */
  constructor(
    private readonly stage: Stage,
    private readonly molecule: Group,
    private readonly hooks: GizmoHooks,
  ) {
    this.controls = new TransformControls(stage.camera, stage.canvas);
    this.controls.size = 0.9;

    stage.scene.add(this.proxy, this.controls.getHelper());

    this.controls.addEventListener("change", () => stage.requestRender());
    this.controls.addEventListener("dragging-changed", (event) => {
      stage.controls.enabled = !event.value;
    });
    this.controls.addEventListener("mouseDown", () => {
      this.start = {
        position: this.molecule.position.clone(),
        rotation: this.molecule.quaternion.clone(),
        pivot: this.proxy.position.clone(),
      };
      this.hooks.start(this.poseOf(this.start.position, this.start.rotation));
    });
    this.controls.addEventListener("objectChange", () => {
      if (this.follow()) {
        this.hooks.change(this.current(), false);
      }
    });
    this.controls.addEventListener("mouseUp", () => {
      if (this.follow()) {
        this.start = null;
        this.hooks.change(this.current(), true);
      }
    });
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
   */
  show(mode: GizmoMode, space: Space, pivot: Vector3): void {
    if (this.start) {
      return;
    }

    this.mode = mode;
    this.proxy.position.copy(pivot);
    this.proxy.quaternion.copy(this.molecule.quaternion);
    this.controls.setMode(mode);
    this.controls.setSpace(space);
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

    this.molecule.position.copy(start.position);
    this.molecule.quaternion.copy(start.rotation);
    this.molecule.updateMatrixWorld(true);
    this.proxy.position.copy(start.pivot);
    this.proxy.quaternion.copy(start.rotation);

    // the controls go on thinking a handle is held until the button comes up, so they are let go of and taken up afresh
    this.controls.detach();
    if (this.mode) {
      this.controls.attach(this.proxy);
    }
    this.stage.requestRender();
    this.hooks.cancel(this.poseOf(start.position, start.rotation));
  }

  /** Releases the handles. */
  dispose(): void {
    this.stage.scene.remove(this.proxy, this.controls.getHelper());
    this.controls.dispose();
  }

  /**
   * Puts the molecule where the proxy now says: turned as the proxy is, about the pivot the proxy began on.
   *
   * @returns whether a handle is held, which is when there is anything to follow
   */
  private follow(): boolean {
    const start = this.start;
    if (!start) {
      return false;
    }

    const turn = this.proxy.quaternion.clone().multiply(start.rotation.clone().invert());
    this.molecule.quaternion.copy(this.proxy.quaternion);
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
