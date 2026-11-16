import {Vector3} from "three";
import type {Stage} from "../../../render/stage";
import type {BondEdit} from "../../session/messages";
import type {Hold} from "../../selection/bondLength";
import {angleAt, planeNormal} from "../../selection/angle";
import {torsion} from "../../selection/torsion";
import type {MoleculeScene} from "../scene/MoleculeScene";
import type {StudioViewListener} from "../StudioViewListener";
import {minus} from "../vec";
import {MeasurementHandles} from "./MeasurementHandles";

/** A handle on a bond or an atom, and which atom stays where it is. */
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

/** The handle on a bond or an atom that changes what is measured there. */
export class BondHandleController {
  private readonly handles: MeasurementHandles;
  /** The handle being offered, or null for none. */
  private target: MeasurementTarget | null = null;

  /**
   * @param stage the stage the handle is drawn on
   * @param scene the molecule the handle measures
   * @param emit tells every listener something
   */
  constructor(
    stage: Stage,
    private readonly scene: MoleculeScene,
    private readonly emit: (tell: (listener: StudioViewListener) => void) => void,
  ) {
    this.handles = new MeasurementHandles(stage, {
      change: (value, done) => this.changed(value, done),
      cancel: () => {
        this.place();
        this.emit((listener) => listener.bondDragCancel?.());
      },
    });
  }

  /** Whether the pointer is over the handle. */
  get hovered(): boolean {
    return this.handles.hovered;
  }

  /** Whether the handle is being dragged. */
  get dragging(): boolean {
    return this.handles.dragging;
  }

  /** Abandons a drag of the handle. */
  cancel(): void {
    this.handles.cancel();
  }

  /**
   * Offers a handle, or takes it away.
   *
   * @param target what the handle does and to which bond, or null for none
   */
  set(target: MeasurementTarget | null): void {
    this.target = target;
    this.place();
  }

  /** Brings the handle to where what it changes is now, or takes it away. A handle being dragged is left alone. */
  place(): void {
    const handle = this.target;
    const scene = this.scene;
    if (!handle) {
      this.handles.hide();
      return;
    }

    if (handle.mode === "angle") {
      const centre = scene.atomById.get(handle.centre);
      const from = scene.atomById.get(handle.from);
      const to = scene.atomById.get(handle.to);
      if (!centre || !from || !to) {
        this.handles.hide();
        return;
      }

      // the ring is at the atom, and turns about the line at right angles to the plane of the two bonds
      const normal = planeNormal(minus(from.at, centre.at), minus(to.at, centre.at));
      const axis = new Vector3(normal.x, normal.y, normal.z).applyQuaternion(scene.content.quaternion);
      this.handles.show("angle", scene.worldOf(centre), axis, angleAt(from.at, centre.at, to.at), 1);
      return;
    }

    const link = scene.bonds.find((bond) => bond.link.bond.id === handle.bond)?.link;
    const first = link && scene.atomById.get(link.bond.first);
    const second = link && scene.atomById.get(link.bond.second);
    if (!link || !first || !second) {
      this.handles.hide();
      return;
    }

    const a = scene.worldOf(first);
    const b = scene.worldOf(second);
    if (handle.mode === "length") {
      // the arrow sits on the atom that moves, which for neither held is the second
      const onFirst = handle.hold === "second";
      this.handles.show("length", onFirst ? a : b, b.clone().sub(a), a.distanceTo(b), onFirst ? -1 : 1);
      return;
    }

    const from = scene.atomById.get(handle.from);
    const to = scene.atomById.get(handle.to);
    if (!from || !to) {
      this.handles.hide();
      return;
    }
    this.handles.show("turn", a.clone().add(b).multiplyScalar(0.5), b.clone().sub(a), torsion(from.at, first.at, second.at, to.at), 1);
  }

  /** Releases what the handle drew. */
  dispose(): void {
    this.handles.dispose();
  }

  /**
   * Tells the listeners where the handle has got to.
   *
   * @param value the length, torsion or angle the handle has put what it changes at
   * @param done whether the handle has just been let go
   */
  private changed(value: number, done: boolean): void {
    const handle = this.target;
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
    this.emit((listener) => listener.bondDrag?.(edit, done));
    if (done) {
      this.place();
    }
  }
}
