import {
  AmbientLight,
  DirectionalLight,
  PerspectiveCamera,
  Scene,
  Vector2,
  Vector3,
  WebGLRenderer,
} from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import { UP } from "./primitives";

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
    this.camera.up.copy(UP);
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

  /** Draws the scene now. */
  render(): void {
    this.renderer.render(this.scene, this.camera);
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
