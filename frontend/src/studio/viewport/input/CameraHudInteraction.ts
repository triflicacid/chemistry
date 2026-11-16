import {FIELD_OF_VIEW, type Stage} from "../../../render/stage";
import type {CameraRig} from "../camera/CameraRig";
import type {CameraLock} from "../camera/CameraLock";
import type {CameraHud} from "../hud/CameraHud";
import type {HudHandle} from "../hud/dial";
import {BALL_TRAVEL} from "../hud/panDial";
import {FOV_RESET_MS} from "./constants";
import type {InteractionHandler} from "./PointerRouter";

/** The cursor over each camera sphere handle. */
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

/** The camera spheres and FOV cone shown with the pan tool. */
export class CameraHudInteraction implements InteractionHandler {
  readonly swallows = true;

  /** The camera sphere being dragged, and where the pointer was at the last step. */
  private drag: { handle: HudHandle; x: number; y: number } | null = null;
  /** When the field-of-view slider was last pressed, from performance.now, or 0 if a press has just been used as a double press. */
  private lastFovPress = 0;

  /**
   * @param stage the stage the spheres are drawn on
   * @param hud the spheres
   * @param rig the camera they move
   * @param lock keeps the camera's own controls off while a sphere is held
   * @param resting the cursor to go back to when a sphere is let go
   */
  constructor(
    private readonly stage: Stage,
    private readonly hud: CameraHud,
    private readonly rig: CameraRig,
    private readonly lock: CameraLock,
    private readonly resting: () => string,
  ) {}

  hitTest(event: PointerEvent): boolean {
    return this.handleAt(event) !== null;
  }

  press(event: PointerEvent): boolean {
    const handle = event.button === 0 ? this.handleAt(event) : null;
    if (!handle) {
      return false;
    }

    this.lock.hold("cameraHud");
    this.stage.canvas.setPointerCapture(event.pointerId);
    this.drag = { handle, x: event.clientX, y: event.clientY };
    this.hud.active = handle;
    this.stage.canvas.style.cursor = "grabbing";

    if (handle === "fov" || handle === "cone") {
      // pressing the slider or cone twice quickly puts the field of view back to the default
      const now = performance.now();
      const again = now - this.lastFovPress < FOV_RESET_MS;
      this.lastFovPress = again ? 0 : now;
      this.rig.setCamera({ fov: again ? FIELD_OF_VIEW : this.fovFromPointer(handle, event) });
    }
    this.stage.requestRender();
    return true;
  }

  /** Moves the view by a step of a drag on a camera sphere. */
  move(event: PointerEvent): void {
    const drag = this.drag;
    if (!drag) {
      return;
    }

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
    const pulled = { x: this.hud.ball.x + across, y: this.hud.ball.y - down };
    const length = Math.hypot(pulled.x, pulled.y);
    const scale = length > BALL_TRAVEL ? BALL_TRAVEL / length : 1;
    this.hud.ball = { x: pulled.x * scale, y: pulled.y * scale };
    this.stage.requestRender();
  }

  release(): void {
    this.endDrag();
  }

  cancel(): void {
    this.endDrag();
  }

  hover(event: PointerEvent, over: boolean): void {
    const handle = over ? this.handleAt(event) : null;
    if (handle !== this.hud.hovered) {
      this.hud.hovered = handle;
      this.stage.requestRender();
    }
  }

  cursor(event: PointerEvent): string | null {
    const handle = this.handleAt(event);
    return handle && HUD_CURSOR[handle];
  }

  leave(): void {
    if (this.hud.hovered) {
      this.hud.hovered = null;
      this.stage.requestRender();
    }
  }

  private handleAt(event: PointerEvent): HudHandle | null {
    return this.hud.hit(this.stage.canvas.getBoundingClientRect(), event.clientX, event.clientY);
  }

  /** The field of view a pointer asks for on the slider, or by where it is about the cone. */
  private fovFromPointer(handle: HudHandle, event: PointerEvent): number {
    const canvas = this.stage.canvas.getBoundingClientRect();
    return handle === "cone" ? this.hud.fovOfCone(canvas, event.clientX, event.clientY) : this.hud.fovAt(canvas, event.clientX);
  }

  /** Lets go of a camera sphere, giving the camera its controls back and the pan ball back its place. */
  private endDrag(): void {
    if (!this.drag) {
      return;
    }
    this.drag = null;
    this.hud.active = null;
    this.hud.ball = { x: 0, y: 0 };
    this.lock.release("cameraHud");
    this.stage.canvas.style.cursor = this.resting();
    this.stage.requestRender();
  }
}
