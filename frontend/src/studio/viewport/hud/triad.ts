import { ArrowHelper, type LineBasicMaterial, type Material, type Mesh, PerspectiveCamera, Scene, Vector2, Vector3, type WebGLRenderer } from "three";
import type { LabelFactory } from "../../../render/labels";

/** The axes' colours, as the scene's own axes draw them. */
const COLOURS = ["#e41a1c", "#2ca02c", "#1f4fd8"];

const NAMES = ["X", "Y", "Z"];

/** How far from the centre the camera sits, in the units the axes are drawn in. */
const DISTANCE = 6;

/** How near, in pixels, a click must be to the end of an axis to pick it. */
const REACH = 18;

/** Where each axis's letter sits, which is also where a click picks it. */
const TIPS = [new Vector3(1.45, 0, 0), new Vector3(0, 1.45, 0), new Vector3(0, 0, 1.45)];

/** A small set of labelled axes drawn in a corner of the canvas. */
export class Triad {
  private readonly scene = new Scene();
  private readonly camera = new PerspectiveCamera(40, 1, 0.1, 50);

  /** Whether to draw it. */
  visible = false;

  /**
   * @param labels makes the letters, and owns what they are drawn from
   * @param size the side of the square it is drawn in, in CSS pixels
   * @param margin how far the square is from the canvas's lower left corner, in CSS pixels
   */
  constructor(
    labels: LabelFactory,
    private readonly size = 110,
    private readonly margin = 12,
  ) {
    [new Vector3(1, 0, 0), new Vector3(0, 1, 0), new Vector3(0, 0, 1)].forEach((direction, i) => {
      const arrow = new ArrowHelper(direction, new Vector3(), 1, COLOURS[i], 0.3, 0.2);
      this.scene.add(arrow);

      const label = labels.label(NAMES[i] as string, COLOURS[i] as string, 0.5);
      label.position.copy(direction).multiplyScalar(1.45);
      this.scene.add(label);
    });
  }

  /**
   * Finds which axis a click on the canvas is on.
   *
   * @param canvas the canvas's box on the page
   * @param x the click's horizontal position on the page
   * @param y the click's vertical position on the page
   * @returns the direction of the axis whose letter is nearest the click and within reach of it, or null if the
   *     triad is hidden or the click is not on a letter
   */
  axisAt(canvas: DOMRect, x: number, y: number): Vector3 | null {
    if (!this.visible) {
      return null;
    }

    // the triad's own pixels, measured from its lower left corner like the viewport it is drawn in
    const px = x - canvas.left - this.margin;
    const py = canvas.bottom - y - this.margin;
    if (px < 0 || py < 0 || px > this.size || py > this.size) {
      return null;
    }

    let nearest: Vector3 | null = null;
    let best = REACH;
    for (const tip of TIPS) {
      const ndc = tip.clone().project(this.camera);
      const distance = Math.hypot(((ndc.x + 1) / 2) * this.size - px, ((ndc.y + 1) / 2) * this.size - py);
      if (distance < best) {
        best = distance;
        nearest = tip.clone().normalize();
      }
    }
    return nearest;
  }

  /**
   * Draws the triad over what the renderer has already drawn, turned as `from` is turned.
   *
   * @param renderer the renderer, which has just drawn the scene
   * @param from the camera the scene was drawn with, whose orientation the triad takes
   */
  draw(renderer: WebGLRenderer, from: PerspectiveCamera): void {
    if (!this.visible) {
      return;
    }

    this.camera.quaternion.copy(from.quaternion);
    this.camera.position.set(0, 0, DISTANCE).applyQuaternion(from.quaternion);

    const whole = renderer.getSize(new Vector2());
    const clears = renderer.autoClear;

    // the triad has the corner to itself, drawn over the scene whatever the scene put in front of it
    renderer.autoClear = false;
    renderer.setScissorTest(true);
    renderer.setViewport(this.margin, this.margin, this.size, this.size);
    renderer.setScissor(this.margin, this.margin, this.size, this.size);
    renderer.clearDepth();
    renderer.render(this.scene, this.camera);

    renderer.setScissorTest(false);
    renderer.setViewport(0, 0, whole.x, whole.y);
    renderer.autoClear = clears;
  }

  /** Frees the materials; the letters' textures belong to the label factory. */
  dispose(): void {
    this.scene.traverse((object) => {
      const material = (object as Mesh).material as Material | Material[] | undefined;
      [material ?? []].flat().forEach((each) => (each as LineBasicMaterial).dispose());
    });
  }
}
