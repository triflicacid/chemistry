import {
    AmbientLight,
    DirectionalLight,
    PerspectiveCamera,
    type Quaternion,
    Scene,
    Vector2,
    Vector3,
    WebGLRenderer,
} from "three";
import {OrbitControls} from "three/examples/jsm/controls/OrbitControls.js";
import {UP} from "./primitives";
import {rolled} from "./roll";

/** The camera's vertical field of view, in degrees. */
export const FIELD_OF_VIEW = 30;

const suppress = (event: Event): void => event.preventDefault();

/** Where a stage is drawn, and what to tell when the view moves. */
export interface StageOptions {
  /** The canvas to draw on; the stage makes its own if there is none, which the caller adds to the page. */
  canvas?: HTMLCanvasElement;
  /** Whose size the canvas takes. The stage follows it as it changes. */
  sizedBy: Element;
  /** Called after the camera or its orbit point has moved, for anything that is placed relative to them. */
  onMove?: () => void;
}

/**
 * A renderer, scene, camera and orbit controls with the lighting and sizing that a molecule view needs.
 *
 * Drawing is deferred to the next animation frame and coalesced.
 */
export class Stage {
  readonly scene = new Scene();
  readonly camera = new PerspectiveCamera(FIELD_OF_VIEW, 1, 0.1, 1000);
  readonly renderer: WebGLRenderer;
  readonly controls: OrbitControls;

  private readonly resizer: ResizeObserver;
  private readonly overlays = new Set<(renderer: WebGLRenderer, camera: PerspectiveCamera, capturing: boolean) => void>();
  private pending = 0;

  constructor(private readonly options: StageOptions) {
    this.renderer = new WebGLRenderer({ canvas: options.canvas, antialias: true, alpha: true });
    this.renderer.setPixelRatio(window.devicePixelRatio);

    this.scene.add(this.camera);
    this.scene.add(new AmbientLight(0xffffff, 0.75));
    const light = new DirectionalLight(0xffffff, 1.1);
    light.position.set(0.4, 0.8, 1);
    this.camera.add(light);

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = false;
    this.controls.addEventListener("change", () => {
      options.onMove?.();
      this.requestRender();
    });

    // right-drag pans, so the menu that a right-click would open must not
    this.canvas.addEventListener("contextmenu", suppress);

    this.resizer = new ResizeObserver(() => this.resize());
    this.resizer.observe(options.sizedBy);
  }

  /** The canvas the stage draws on. */
  get canvas(): HTMLCanvasElement {
    return this.renderer.domElement;
  }

  /**
   * Points the camera at a sphere from the default angle, far enough back to fit it, and orbits its centre.
   *
   * @param centre the middle of what to show
   * @param radius how far from `centre` the things to show reach
   */
  frame(centre: Vector3, radius: number): void {
    const distance = (1.1 * radius) / Math.sin((FIELD_OF_VIEW * Math.PI) / 360);
    const yaw = (30 * Math.PI) / 180;
    const pitch = (20 * Math.PI) / 180;
    this.camera.position.set(
      Math.cos(pitch) * Math.sin(yaw) * distance,
      Math.sin(pitch) * distance,
      Math.cos(pitch) * Math.cos(yaw) * distance,
    ).add(centre);
    this.controls.target.copy(centre);
    this.orient(UP);
    this.controls.update();
    this.options.onMove?.();
    this.requestRender();
  }

  /**
   * Moves the point the view orbits, carrying the camera with it so that the view keeps its angle.
   *
   * @param to the new orbit point
   */
  pivotOn(to: Vector3): void {
    this.camera.position.add(to.clone().sub(this.controls.target));
    this.controls.target.copy(to);
    this.controls.update();
    this.options.onMove?.();
    this.requestRender();
  }

  /**
   * Turns the camera to look at its orbit point from along a direction, from as far away as it is now.
   *
   * @param from the direction from the orbit point to where the camera goes, such as +x to look at it from the right
   */
  lookFrom(from: Vector3): void {
    // looking along an axis is asking for that view, not for it as it was tilted
    this.orient(UP);
    const distance = this.camera.position.distanceTo(this.controls.target);
    this.camera.position.copy(this.controls.target).addScaledVector(from.clone().normalize(), distance);
    this.controls.update();
    this.options.onMove?.();
    this.requestRender();
  }

  /**
   * Rolls the camera about the direction it looks in, which turns the picture in the screen's plane about the middle of
   * the view and moves nothing in the scene. A positive angle turns the picture counterclockwise.
   * <p>
   * It is the camera's own up that is turned, so it still rolls when the camera looks along the scene's up. Orbiting
   * afterwards keeps the roll, turning about the tilted up.
   *
   * @param angle how far to roll, in radians
   */
  roll(angle: number): void {
    const forward = this.controls.target.clone().sub(this.camera.position);
    const screenUp = new Vector3(0, 1, 0).applyQuaternion(this.camera.quaternion);
    this.orient(rolled(screenUp, forward, angle));
    this.controls.update();
    this.options.onMove?.();
    this.requestRender();
  }

  /** Takes the roll out, so that up on the screen is the scene's up again, and the camera still looks the same way. */
  level(): void {
    this.orient(UP);
    this.controls.update();
    this.options.onMove?.();
    this.requestRender();
  }

  /**
   * Says which way is up. The orbit controls work out, once, when they are made, how to turn the scene so that up is
   * +y, which they orbit about, and do not look at the camera's up again. Without telling them, orbiting after a roll
   * would still turn about the old up and the picture would swing.
   *
   * @param up which way is up on the screen, as a unit vector in the scene
   */
  private orient(up: Vector3): void {
    this.camera.up.copy(up);
    const held = this.controls as unknown as { _quat: Quaternion; _quatInverse: Quaternion };
    held._quat.setFromUnitVectors(up, new Vector3(0, 1, 0));
    held._quatInverse.copy(held._quat).invert();
  }

  /**
   * Converts the position of a pointer event to where it falls on the canvas.
   *
   * @param event any event that carries client coordinates
   * @returns the point in normalised device coordinates, from -1 to 1 with y up
   */
  pointer(event: { clientX: number; clientY: number }): Vector2 {
    const box = this.canvas.getBoundingClientRect();
    return new Vector2(((event.clientX - box.left) / box.width) * 2 - 1, -((event.clientY - box.top) / box.height) * 2 + 1);
  }

  /** Asks for the scene to be drawn on the next animation frame, unless a draw is already waiting. */
  requestRender(): void {
    if (this.pending === 0) {
      this.pending = requestAnimationFrame(() => {
        this.pending = 0;
        this.render();
      });
    }
  }

  /**
   * Adds something to draw over the scene each time it is drawn, such as a corner widget.
   *
   * @param draw draws it, given the renderer, the camera the scene was just drawn with, and whether this is for a picture
   *     being saved, in which case something that is only there to be used, such as a dial, is left out
   * @returns how to take it away again
   */
  addOverlay(draw: (renderer: WebGLRenderer, camera: PerspectiveCamera, capturing: boolean) => void): () => void {
    this.overlays.add(draw);
    return () => this.overlays.delete(draw);
  }

  /**
   * Draws the scene now, then whatever has been added to draw over it.
   *
   * @param capturing whether this is the drawing a picture is read from
   */
  render(capturing = false): void {
    this.renderer.render(this.scene, this.camera);
    this.overlays.forEach((draw) => draw(this.renderer, this.camera, capturing));
  }

  /**
   * Draws the scene now and reads the canvas back as an image, exactly as the camera sees it.
   *
   * @returns a PNG data URL, with the background transparent wherever nothing is drawn
   */
  capture(): string {
    // the drawing buffer is cleared once the page has shown it, so it is read in the same task that draws it
    this.render(true);
    return this.renderer.domElement.toDataURL("image/png");
  }

  /** Stops following the size, cancels any waiting draw, and releases the renderer and controls. */
  dispose(): void {
    cancelAnimationFrame(this.pending);
    this.pending = 0;
    this.resizer.disconnect();
    this.canvas.removeEventListener("contextmenu", suppress);
    this.controls.dispose();
    this.renderer.dispose();
  }

  private resize(): void {
    const width = Math.max(1, this.options.sizedBy.clientWidth);
    const height = Math.max(1, this.options.sizedBy.clientHeight);
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.requestRender();
  }
}
