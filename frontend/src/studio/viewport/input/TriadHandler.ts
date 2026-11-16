import type {Stage} from "../../../render/stage";
import type {CameraRig} from "../camera/CameraRig";
import {SAME_VIEW} from "../camera/CameraRig";
import type {Triad} from "../hud/triad";
import type {InteractionHandler} from "./PointerRouter";

export class TriadHandler implements InteractionHandler {
  readonly swallows = true;

  constructor(
    private readonly stage: Stage,
    private readonly triad: Triad,
    private readonly rig: CameraRig,
  ) {}

  hitTest(event: PointerEvent): boolean {
    return this.triad.axisAt(this.stage.canvas.getBoundingClientRect(), event.clientX, event.clientY) !== null;
  }

  press(event: PointerEvent): boolean {
    if (event.button !== 0) {
      return false;
    }
    const axis = this.triad.axisAt(this.stage.canvas.getBoundingClientRect(), event.clientX, event.clientY);
    if (!axis) {
      return false;
    }

    // the same letter again, with the camera where the last click left it, goes round to the other side
    const last = this.rig.snapped;
    const again = last !== null && last.axis.dot(axis) > 0.99 && this.stage.camera.quaternion.angleTo(last.orientation) < SAME_VIEW;
    const sign = again && last.sign === 1 ? -1 : 1;

    this.stage.lookFrom(axis.clone().multiplyScalar(sign));
    this.rig.snapped = { axis, sign, orientation: this.stage.camera.quaternion.clone() };
    return true;
  }

  cursor(): string {
    return "pointer";
  }
}
