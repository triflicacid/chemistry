import {MOUSE, TOUCH} from "three";
import {SelectMode} from "./SelectMode";
import type {ModeHost} from "./ToolMode";

/** Move the view and camera. */
export class PanMode extends SelectMode {
  /**
   * @param host the view the mode is for
   */
  constructor(host: ModeHost) {
    super(host, "pan");
  }

  protected override get picks(): boolean {
    return false;
  }

  override enter(): void {
    const { stage, cameraHud } = this.host;
    this.remap(true);
    stage.canvas.style.cursor = "grab";
    // the spheres are the camera's controls, so they are there while the view is what is being worked on
    cameraHud.visible = true;
    stage.requestRender();
  }

  override exit(): void {
    const { stage, cameraHud } = this.host;
    this.remap(false);
    stage.canvas.style.cursor = "";
    cameraHud.visible = false;
    cameraHud.hovered = null;
    stage.requestRender();
  }

  override resting(): string {
    return "grab";
  }

  override press(event: PointerEvent): boolean {
    if (event.button === 0) {
      this.host.stage.canvas.style.cursor = "grabbing";
      this.host.panGuide.begin();
    }
    return super.press(event);
  }

  override release(event: PointerEvent): void {
    if (event.button === 0) {
      this.host.stage.canvas.style.cursor = "grab";
      this.host.panGuide.end();
    }
    super.release(event);
  }

  override cancel(): void {
    this.host.panGuide.end();
    super.cancel();
  }

  private remap(panning: boolean): void {
    const controls = this.host.stage.controls;
    controls.mouseButtons.LEFT = panning ? MOUSE.PAN : MOUSE.ROTATE;
    controls.mouseButtons.RIGHT = panning ? MOUSE.ROTATE : MOUSE.PAN;
    controls.touches.ONE = panning ? TOUCH.PAN : TOUCH.ROTATE;
  }
}
