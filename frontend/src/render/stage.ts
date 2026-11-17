import {
  AmbientLight,
  DirectionalLight,
  PerspectiveCamera,
  PMREMGenerator,
  type Quaternion,
  Scene,
  type Texture,
  Vector2,
  Vector3,
  WebGLRenderer,
} from "three";
import {RoomEnvironment} from "three/examples/jsm/environments/RoomEnvironment.js";
import {OrbitControls} from "three/examples/jsm/controls/OrbitControls.js";
import {UP} from "./primitives";
import {rolled, rollOf} from "./roll";

/** The camera's vertical field of view, in degrees. */
export const FIELD_OF_VIEW = 30;

/** How far round the scene's up, from the +z axis, and how far above level, in degrees, the camera is when it frames something. */
export const DEFAULT_YAW = 30;
export const DEFAULT_PITCH = 20;

const suppress = (event: Event): void => event.preventDefault();

const AMBIENT_LIGHT = 0.75;
const KEY_LIGHT = 1.1;
const ENVIRONMENT_LIGHT = 0.7;

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

  /** The light that reaches everything. */
  private readonly key = new DirectionalLight(0xffffff, KEY_LIGHT);

  /** The light coming from the camera. */
  private readonly ambient = new AmbientLight(0xffffff, AMBIENT_LIGHT);

  private readonly resizer: ResizeObserver;
  private readonly overlays = new Set<(renderer: WebGLRenderer, camera: PerspectiveCamera, capturing: boolean) => void>();
  private pending = 0;
  /** What shiny surfaces reflect. */
  private environment: Texture | null = null;
  /** Where the camera stood when a look-around drag began, while one is under way. */
  private eye: Vector3 | null = null;
  private pressed = -1;

  /** Whether a drag that would orbit the camera turns it where it stands instead, which the left button does. */
  lookAround = false;

  constructor(private readonly options: StageOptions) {
    this.renderer = new WebGLRenderer({ canvas: options.canvas, antialias: true, alpha: true });
    this.renderer.setPixelRatio(window.devicePixelRatio);

    this.scene.add(this.camera);
    this.scene.add(this.ambient);
    this.key.position.set(0.4, 0.8, 1);
    this.camera.add(this.key);

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = false;
    // before the controls hear it, so that their start finds which button it was
    this.canvas.addEventListener("pointerdown", (event) => (this.pressed = event.button), { capture: true });
    this.controls.addEventListener("start", () => {
      this.eye = this.lookAround && this.pressed === 0 ? this.camera.position.clone() : null;
    });
    this.controls.addEventListener("end", () => (this.eye = null));
    this.controls.addEventListener("change", () => {
      if (this.eye) {
        // the controls orbited the camera about the target; the same turn about the camera is the camera looking round
        const forward = this.controls.target.clone().sub(this.camera.position);
        this.camera.position.copy(this.eye);
        this.controls.target.copy(this.eye).add(forward);
      }
      options.onMove?.();
      this.requestRender();
    });

    // right-drag pans, so the menu that a right-click would open must not
    this.canvas.addEventListener("contextmenu", suppress);

    this.resizer = new ResizeObserver(() => this.resize());
    this.resizer.observe(options.sizedBy);
  }

  /**
   * Lets surfaces reflect a studio-like room.
   *
   * @param on whether surfaces reflect it
   */
  setEnvironment(on: boolean): void {
    if (on && !this.environment) {
      const generator = new PMREMGenerator(this.renderer);
      this.environment = generator.fromScene(new RoomEnvironment(), 0.04).texture;
      generator.dispose();
    }
    this.scene.environment = on ? this.environment : null;
    this.requestRender();
  }

  /**
   * Makes the scene brighter or dimmer.
   *
   * @param factor a multiple of the usual strength: 1 leaves the lighting as it is
   */
  setBrightness(factor: number): void {
    this.ambient.intensity = AMBIENT_LIGHT * factor;
    this.key.intensity = KEY_LIGHT * factor;
    this.scene.environmentIntensity = ENVIRONMENT_LIGHT * factor;
    this.requestRender();
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
    const distance = (1.1 * radius) / Math.sin((this.camera.fov * Math.PI) / 360);
    const yaw = (DEFAULT_YAW * Math.PI) / 180;
    const pitch = (DEFAULT_PITCH * Math.PI) / 180;
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

  /** How far the camera is rolled from level, in radians, counterclockwise positive. */
  get rolledBy(): number {
    const forward = this.controls.target.clone().sub(this.camera.position);
    return rollOf(new Vector3(0, 1, 0).applyQuaternion(this.camera.quaternion), forward);
  }

  /**
   * Puts the camera somewhere, looking at a point, which it then orbits, rolled as asked.
   *
   * @param position where the camera goes
   * @param target the point it looks at
   * @param fov the camera's vertical field of view, in degrees
   * @param roll how far to roll it from level, in radians
   */
  place(position: Vector3, target: Vector3, fov: number, roll: number): void {
    this.camera.position.copy(position);
    this.controls.target.copy(target);
    if (fov !== this.camera.fov) {
      this.camera.fov = fov;
      this.camera.updateProjectionMatrix();
    }
    this.orient(UP);
    this.controls.update();
    if (roll !== 0) {
      this.roll(roll);
    }
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
    this.environment?.dispose();
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
