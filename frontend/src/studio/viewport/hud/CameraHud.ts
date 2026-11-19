import {OrthographicCamera, type PerspectiveCamera, type Scene, Vector2, type WebGLRenderer} from "three";

import {HUD_GAP, HUD_MARGIN, HUD_SIZE} from "./constants";
import {FovDial, fovOfSliderOffset} from "./fovDial";
import {FlyDial} from "./flyDial";
import {OrbitDial} from "./orbitDial";
import {ACTIVE, type HudHandle, type HudPoint, HOVERED, type Look, RESTING} from "./dial";

/**
 * Three dials in the lower right corner of the canvas to move the camera with, drawn like the triad and the roll ring: an
 * orbit sphere, a fly stick and a field-of-view cone. They are in a row, the orbit sphere on the right.
 */
export class CameraHud {
  /** Whether to draw them, and take presses. */
  visible = false;

  /** What the pointer is over, which brightens it. */
  hovered: HudHandle | null = null;

  /** What is being dragged, which brightens it further. */
  active: HudHandle | null = null;

  private readonly camera = new OrthographicCamera(-HUD_SIZE / 2, HUD_SIZE / 2, HUD_SIZE / 2, -HUD_SIZE / 2, -200, 200);
  private readonly orbit = new OrbitDial();
 private readonly fly = new FlyDial();
  private readonly fov = new FovDial();

  /** Where the fly stick's ball is, from its centre, in pixels with y up, which the view sets as it is pulled. */
  get stick(): HudPoint {
    return this.fly.ball;
  }

  set stick(point: HudPoint) {
    this.fly.ball = point;
  }

  /** How far the fly stick's lift ring is turned, from -1 to 1 with rising positive, which the view sets as it is turned. */
  get lift(): number {
    return this.fly.lift;
  }

  set lift(amount: number) {
    this.fly.lift = amount;
  }

  /**
   * The angle a pointer is at round the fly stick, which is what turns its lift ring.
   *
   * @param canvas the canvas's box on the page
   * @param x the pointer's horizontal position on the page
   * @param y the pointer's vertical position on the page
   * @returns the angle in radians, counterclockwise from the right
   */
  flyAngleAt(canvas: DOMRect, x: number, y: number): number {
    const offset = this.offsetFrom(canvas, x, y, this.flyCentre(canvas.width));
    return Math.atan2(offset.y, offset.x);
  }

  /**
   * The field of view a pointer at a place on the slider asks for.
   *
   * @param canvas the canvas's box on the page
   * @param x the pointer's horizontal position on the page
   * @returns the field of view in degrees, which is the end's if the pointer is beyond the track
   */
  fovAt(canvas: DOMRect, x: number): number {
    return fovOfSliderOffset(x - canvas.left - this.fovCentre(canvas.width));
  }

  /**
   * The field of view a pointer at a place asks for by pulling the cone.
   *
   * @param canvas the canvas's box on the page
   * @param x the pointer's horizontal position on the page
   * @param y the pointer's vertical position on the page
   */
  fovOfCone(canvas: DOMRect, x: number, y: number): number {
    return this.fov.fovOfCone(this.offsetFrom(canvas, x, y, this.fovCentre(canvas.width)));
  }

  /**
   * @param canvas the canvas's box on the page
   * @param x the pointer's horizontal position on the page
   * @param y the pointer's vertical position on the page
   * @returns what the pointer is over, or null if they are hidden or it is over none
   */
  hit(canvas: DOMRect, x: number, y: number): HudHandle | null {
    if (!this.visible) {
      return null;
    }
    const orbit = this.offsetFrom(canvas, x, y, this.orbitCentre(canvas.width));
   const fly = this.offsetFrom(canvas, x, y, this.flyCentre(canvas.width));
    const fov = this.offsetFrom(canvas, x, y, this.fovCentre(canvas.width));
    return this.orbit.hit(orbit.x, orbit.y) ?? this.fly.hit(fly.x, fly.y) ?? this.fov.hit(fov.x, fov.y);
  }

  /** The distance between the dials' centres, which are in a row. */
  private get spacing(): number {
    return HUD_SIZE + HUD_GAP;
  }

  private orbitCentre(width: number): number {
    return width - HUD_MARGIN - HUD_SIZE / 2;
  }

  private flyCentre(width: number): number {
    return this.orbitCentre(width) - this.spacing;
  }

  private fovCentre(width: number): number {
    return this.flyCentre(width) - this.spacing;
  }

  private offsetFrom(canvas: DOMRect, x: number, y: number, centreX: number): HudPoint {
    return { x: x - canvas.left - centreX, y: canvas.bottom - HUD_MARGIN - HUD_SIZE / 2 - y };
  }

  private look = (handle: HudHandle): Look =>
    this.active === handle ? ACTIVE : this.hovered === handle && this.active === null ? HOVERED : RESTING;

  /**
   * Draws them over what the renderer has already drawn.
   *
   * @param renderer the renderer, which has just drawn the scene
   * @param from the camera the scene was drawn with, whose orientation the orbit sphere takes and whose field of view the
   *     cone shows
   */
  draw(renderer: WebGLRenderer, from: PerspectiveCamera): void {
    if (!this.visible) {
      return;
    }

    [this.orbit, this.fly, this.fov].forEach((widget) => {
      widget.update(from);
      widget.paint(this.look);
    });

    const whole = renderer.getSize(new Vector2());
    const clears = renderer.autoClear;
    renderer.autoClear = false;
    renderer.setScissorTest(true);
    this.render(renderer, this.orbit.scene, this.orbitCentre(whole.x) - HUD_SIZE / 2);
   this.render(renderer, this.fly.scene, this.flyCentre(whole.x) - HUD_SIZE / 2);
    this.render(renderer, this.fov.scene, this.fovCentre(whole.x) - HUD_SIZE / 2);
    renderer.setScissorTest(false);
    renderer.setViewport(0, 0, whole.x, whole.y);
    renderer.autoClear = clears;
  }

  private render(renderer: WebGLRenderer, scene: Scene, left: number): void {
    renderer.setViewport(left, HUD_MARGIN, HUD_SIZE, HUD_SIZE);
    renderer.setScissor(left, HUD_MARGIN, HUD_SIZE, HUD_SIZE);
    renderer.clearDepth();
    renderer.render(scene, this.camera);
  }

  /** Frees what they are drawn from. */
  dispose(): void {
    [this.orbit, this.fly, this.fov].forEach((widget) => widget.dispose());
  }
}
