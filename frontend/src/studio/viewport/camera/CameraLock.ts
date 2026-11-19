import type {Stage} from "../../../render/stage";

/** Who may be keeping the camera's own controls from answering the pointer. */
export type CameraHolder = "cameraHud" | "rollRing" | "transformHandle" | "moleculeHandles" | "bondHandles" | "atomDrag" | "ringAim" | "marquee";

/** Keeps the camera's controls off while anything else has hold of the pointer. */
export class CameraLock {
  private readonly holders = new Set<CameraHolder>();

  /**
   * @param stage the stage whose camera is held
   */
  constructor(private readonly stage: Stage) {}

  /**
   * Keeps the camera still.
   *
   * @param holder who is holding it
   */
  hold(holder: CameraHolder): void {
    this.holders.add(holder);
    this.apply();
  }

  /**
   * Lets go of the camera.
   *
   * @param holder who is letting go
   */
  release(holder: CameraHolder): void {
    this.holders.delete(holder);
    this.apply();
  }

  private apply(): void {
    this.stage.controls.enabled = this.holders.size === 0;
  }
}
