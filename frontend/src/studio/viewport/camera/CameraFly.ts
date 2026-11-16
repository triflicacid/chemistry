import type {Stage} from "../../../render/stage";
import {isTyping} from "../../keys/typing";
import type {CameraRig} from "./CameraRig";

/** How far the camera flies in a second at full speed, as a fraction of how far it is from what it looks at. */
const SPEED = 0.8;

/** How much faster Shift makes keyboard flight. */
const FAST = 3;

/** The keys that fly the camera, and which way each goes: right, up and forward. */
const KEY_AXES: Record<string, readonly [number, number, number]> = {
  d: [1, 0, 0],
  a: [-1, 0, 0],
  e: [0, 1, 0],
  q: [0, -1, 0],
  w: [0, 0, 1],
  s: [0, 0, -1],
};

/** Flies the camera. */
export class CameraFly {
  private readonly keys = new Set<string>();
  private stick = { x: 0, y: 0 };
  private fast = false;
  private frame = 0;
  private last = 0;
  private on = false;

  /**
   * @param stage the stage whose camera flies
   * @param rig moves the camera
   */
  constructor(
    private readonly stage: Stage,
    private readonly rig: CameraRig,
  ) {
    window.addEventListener("keydown", this.onKeyDown);
    window.addEventListener("keyup", this.onKeyUp);
    window.addEventListener("blur", this.stop);
  }

  /** Whether the keys fly the camera. */
  get enabled(): boolean {
    return this.on;
  }

  set enabled(on: boolean) {
    this.on = on;
    if (!on) {
      this.stop();
    }
  }

  /**
   * Holds the stick out, which flies the camera until it is let go.
   *
   * @param x how far to the right, from -1 to 1
   * @param y how far forward, from -1 to 1
   */
  setStick(x: number, y: number): void {
    this.stick = { x, y };
    this.run();
  }

  /** Lets go of every key and the stick, which stops the camera. */
  readonly stop = (): void => {
    this.keys.clear();
    this.stick = { x: 0, y: 0 };
    cancelAnimationFrame(this.frame);
    this.frame = 0;
  };

  /** Stops listening. */
  dispose(): void {
    this.stop();
    window.removeEventListener("keydown", this.onKeyDown);
    window.removeEventListener("keyup", this.onKeyUp);
    window.removeEventListener("blur", this.stop);
  }

  private readonly onKeyDown = (event: KeyboardEvent): void => {
    const key = event.key.toLowerCase();
    if (!this.on || !(key in KEY_AXES) || event.ctrlKey || event.metaKey || event.altKey || isTyping(event)) {
      return;
    }
    this.fast = event.shiftKey;
    this.keys.add(key);
    this.run();
  };

  private readonly onKeyUp = (event: KeyboardEvent): void => {
    this.fast = event.shiftKey;
    this.keys.delete(event.key.toLowerCase());
  };

  /** Starts the flight if it is not under way. */
  private run(): void {
    if (this.frame === 0 && this.wanted()) {
      this.last = performance.now();
      this.frame = requestAnimationFrame(this.step);
    }
  }

  private wanted(): boolean {
    return this.keys.size > 0 || this.stick.x !== 0 || this.stick.y !== 0;
  }

  private readonly step = (now: number): void => {
    this.frame = 0;
    if (!this.wanted()) {
      return;
    }

    let right = this.stick.x;
    let up = 0;
    let forward = this.stick.y;
    for (const key of this.keys) {
      const [x, y, z] = KEY_AXES[key] ?? [0, 0, 0];
      right += x;
      up += y;
      forward += z;
    }

    const { camera, controls } = this.stage;
    const seconds = Math.min((now - this.last) / 1000, 0.1);
    this.last = now;
    const reach = camera.position.distanceTo(controls.target) * SPEED * seconds * (this.fast ? FAST : 1);
    this.rig.moveBy(right * reach, up * reach, forward * reach);
    this.run();
  };
}
