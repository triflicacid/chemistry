import { ArrowHelper, type LineBasicMaterial, type Material, type Mesh, PerspectiveCamera, Scene, Vector2, Vector3, type WebGLRenderer } from "three";
import type { LabelFactory } from "./labels";

/** The axes' colours, as the scene's own axes draw them. */
const COLOURS = ["#e41a1c", "#2ca02c", "#1f4fd8"];

const NAMES = ["X", "Y", "Z"];

/** How far from the centre the camera sits, in the units the axes are drawn in. */
const DISTANCE = 6;

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
