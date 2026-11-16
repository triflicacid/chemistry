import {MathUtils, Object3D, Quaternion, Vector3} from "three";
import {TransformControls} from "three/examples/jsm/controls/TransformControls.js";
import type {Stage} from "../../../render/stage";

/**
 * What the handle does: slide along a bond to change its length, turn about it to change its torsion, or turn about an
 * atom, at right angles to the plane of two of its bonds, to change the angle between them.
 */
export type MeasurementMode = "length" | "turn" | "angle";

/** The shortest and longest a drag will make a bond, in angstroms, whichever way round, which are the limits the session accepts. */
export const MIN_DRAGGED_LENGTH = 0.1;
export const MAX_DRAGGED_LENGTH = 10;

export interface MeasurementHooks {
  /** The handle has put the bond at `value`. */
  change(value: number, done: boolean): void;
  /** The drag was abandoned. */
  cancel(): void;
}

/**
 * How long a bond is once a handle has slid along it.
 * <p>
 * The length is signed, measured along the bond as it was when the handle was taken hold of. Slid past nothing, it goes
 * negative, which puts the atoms on the other side of each other. A bond has no direction at no length, so the value
 * jumps across the gap between the shortest length either way round.
 *
 * @param start how long it was when the handle was taken hold of
 * @param slid how far the handle has moved along the bond, from the first atom towards the second, in angstroms
 * @param sign whether the handle is on the atom that moves with the handle (1, the second atom) or away from it (-1, the first)
 * @returns the signed length, with a size within what the session accepts
 */
export function draggedLength(start: number, slid: number, sign: 1 | -1): number {
  const length = start + sign * slid;
  const size = MathUtils.clamp(Math.abs(length), MIN_DRAGGED_LENGTH, MAX_DRAGGED_LENGTH);
  return length < 0 ? -size : size;
}

/**
 * How large a torsion is once a handle has turned about the bond.
 *
 * @param start the torsion when the handle was taken hold of, in degrees
 * @param turn how the handle has turned since, as a rotation about the bond's axis from the first atom towards the second
 * @returns the torsion in degrees, which is the start plus the turn and is not wrapped into a half circle
 */
export function draggedTorsion(start: number, turn: Quaternion): number {
  // a turn about one axis is (sin(a/2) along it, cos(a/2)), which gives the angle back whichever way round it went
  return start + MathUtils.radToDeg(2 * Math.atan2(turn.x, turn.w));
}

/**
 * How wide an angle is once a handle has turned about its axis.
 * <p>
 * The angle is a full turn, measured from one arm to the other about the axis the handle turns about. Turned on past a
 * straight line it carries on, and the arm goes round to the other side of the plane, so it is not wrapped into half a
 * circle, and the session treats an angle a whole turn more or less as the same.
 *
 * @param start the angle when the handle was taken hold of, in degrees
 * @param turn how the handle has turned since, as a rotation about the axis that opens the angle
 * @returns the angle in degrees
 */
export function draggedAngle(start: number, turn: Quaternion): number {
  return draggedTorsion(start, turn);
}

/**
 * A handle on a bond or an atom: an arrow along a bond that slides to change its length, a ring about a bond that turns
 * to change its torsion, or a ring about an atom that turns to change the angle between two of its bonds.
 */
export class MeasurementHandles {
  private readonly controls: TransformControls;
  /** Stands where the handle is, with its x axis along the bond, which the controls move and this reads. */
  private readonly proxy = new Object3D();
  private mode: MeasurementMode | null = null;

  /** The unit vector along the bond, in the scene, from its first atom towards its second. */
  private axis = new Vector3(1, 0, 0);

  /** Which way the handle's slide changes the length: 1 if it sits on the atom that moves with it, -1 if on the other. */
  private sign: 1 | -1 = 1;

  /** The length or torsion now, which a drag is measured from. */
  private value = 0;

  /** Where the handle was, and what the bond measured, when it was taken hold of. */
  private start: { position: Vector3; rotation: Quaternion; value: number } | null = null;

  /**
   * @param stage what the handle is drawn in and what it stops turning the camera of
   * @param hooks who to tell what happens
   */
  constructor(
    private readonly stage: Stage,
    private readonly hooks: MeasurementHooks,
  ) {
    this.controls = new TransformControls(stage.camera, stage.canvas);
    this.controls.size = 0.8;
    this.controls.setSpace("local");
    // the handle's x axis is the bond's, and the others are not wanted
    this.controls.showY = false;
    this.controls.showZ = false;

    stage.scene.add(this.proxy, this.controls.getHelper());

    this.controls.addEventListener("change", () => stage.requestRender());
    this.controls.addEventListener("dragging-changed", (event) => {
      stage.controls.enabled = !event.value;
    });
    this.controls.addEventListener("mouseDown", () => {
      this.start = { position: this.proxy.position.clone(), rotation: this.proxy.quaternion.clone(), value: this.value };
    });
    this.controls.addEventListener("objectChange", () => this.report(false));
    this.controls.addEventListener("mouseUp", () => {
      if (this.start) {
        this.report(true);
        this.start = null;
      }
    });
  }

  /** Whether the pointer is over the handle, so that pressing would take hold of it. */
  get hovered(): boolean {
    return this.mode !== null && this.controls.axis !== null;
  }

  /** Whether the handle is held. */
  get dragging(): boolean {
    return this.start !== null;
  }

  /**
   * Shows the handle, or moves it to where the bond now is. Does nothing while the handle is held.
   *
   * @param mode what the handle does
   * @param at where it sits, in the scene
   * @param axis the unit vector along the bond, from its first atom towards its second, in the scene
   * @param value the bond's length in angstroms, or its torsion in degrees, which a drag is measured from
   * @param sign for a length handle, which way sliding it changes the length: 1 on the atom that moves with it, -1 on the other
   */
  show(mode: MeasurementMode, at: Vector3, axis: Vector3, value: number, sign: 1 | -1): void {
    if (this.start) {
      return;
    }

    this.mode = mode;
    this.axis = axis.clone().normalize();
    this.sign = sign;
    this.value = value;
    this.proxy.position.copy(at);
    this.proxy.quaternion.setFromUnitVectors(new Vector3(1, 0, 0), this.axis);
    this.controls.setMode(mode === "length" ? "translate" : "rotate");
    this.controls.attach(this.proxy);
    this.stage.requestRender();
  }

  /** Takes the handle away. A drag in progress is abandoned. */
  hide(): void {
    if (this.start) {
      this.cancel();
    }
    this.mode = null;
    this.controls.detach();
    this.stage.requestRender();
  }

  /** Abandons the drag in progress, if there is one. The owner puts the bond back, and the handle returns to the bond. */
  cancel(): void {
    const start = this.start;
    if (!start) {
      return;
    }
    this.start = null;

    this.proxy.position.copy(start.position);
    this.proxy.quaternion.copy(start.rotation);

    // the controls go on thinking the handle is held until the button comes up, so they are let go of and taken up afresh
    this.controls.detach();
    if (this.mode) {
      this.controls.attach(this.proxy);
    }
    this.stage.requestRender();
    this.hooks.cancel();
  }

  /** Releases the handle. */
  dispose(): void {
    this.stage.scene.remove(this.proxy, this.controls.getHelper());
    this.controls.dispose();
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

    if (this.mode === "length") {
      const slid = this.proxy.position.clone().sub(start.position).dot(this.axis);
      this.hooks.change(draggedLength(start.value, slid, this.sign), done);
    } else {
      const turn = start.rotation.clone().invert().multiply(this.proxy.quaternion);
      this.hooks.change(this.mode === "angle" ? draggedAngle(start.value, turn) : draggedTorsion(start.value, turn), done);
    }
  }
}
