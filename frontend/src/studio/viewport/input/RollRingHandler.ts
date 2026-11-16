import type {Stage} from "../../../render/stage";
import {sweep} from "../../../render/roll";
import type {RollRing} from "../hud/rollRing";
import type {CameraLock} from "../camera/CameraLock";
import {DOUBLE_CLICK_MS} from "./constants";
import type {InteractionHandler} from "./PointerRouter";

export class RollRingHandler implements InteractionHandler {
  readonly swallows = true;

  /** The angle the pointer was last at round the ring, or null while the ring is not being turned. */
  private rolling: { last: number } | null = null;
  /** When the ring was last pressed. */
  private lastPress = 0;

  /**
   * @param stage the stage whose camera is rolled
   * @param ring the ring
   * @param lock keeps the camera's own controls off while the ring is held
   * @param resting the cursor to go back to when the ring is let go
   */
  constructor(
    private readonly stage: Stage,
    private readonly ring: RollRing,
    private readonly lock: CameraLock,
    private readonly resting: () => string,
  ) {}

  hitTest(event: PointerEvent): boolean {
    return this.ring.hit(this.stage.canvas.getBoundingClientRect(), event.clientX, event.clientY);
  }

  press(event: PointerEvent): boolean {
    const box = this.stage.canvas.getBoundingClientRect();
    if (event.button !== 0 || !this.ring.hit(box, event.clientX, event.clientY)) {
      return false;
    }

    const now = performance.now();
    if (now - this.lastPress < DOUBLE_CLICK_MS) {
      this.lastPress = 0;
      this.stage.level();
      return true;
    }
    this.lastPress = now;

    this.lock.hold("rollRing");
    this.stage.canvas.setPointerCapture(event.pointerId);
    this.stage.canvas.style.cursor = "grabbing";
    this.rolling = { last: this.ring.angleAt(box, event.clientX, event.clientY) };
    this.ring.active = true;
    this.stage.requestRender();
    return true;
  }

  move(event: PointerEvent): void {
    const rolling = this.rolling;
    if (rolling) {
      // the picture turns as far as the pointer has gone round the ring since the last move, however far from it
      const angle = this.ring.angleAt(this.stage.canvas.getBoundingClientRect(), event.clientX, event.clientY);
      this.stage.roll(sweep(rolling.last, angle));
      rolling.last = angle;
    }
  }

  release(): void {
    this.endRoll();
  }

  cancel(): void {
    this.endRoll();
  }

  hover(_event: PointerEvent, over: boolean): void {
    if (over !== this.ring.hovered) {
      this.ring.hovered = over;
      this.stage.requestRender();
    }
  }

  cursor(): string {
    return "grab";
  }

  leave(): void {
    if (this.ring.hovered) {
      this.ring.hovered = false;
      this.stage.requestRender();
    }
  }

  /** Lets go of the roll ring, giving the camera its controls back. */
  private endRoll(): void {
    if (!this.rolling) {
      return;
    }
    this.rolling = null;
    this.ring.active = false;
    this.lock.release("rollRing");
    this.stage.canvas.style.cursor = this.resting();
    this.stage.requestRender();
  }
}
