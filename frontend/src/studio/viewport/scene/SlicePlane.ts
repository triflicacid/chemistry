import {
  BufferGeometry,
  CanvasTexture,
  DoubleSide,
  Float32BufferAttribute,
  LinearFilter,
  Mesh,
  MeshBasicMaterial,
  Raycaster,
  SRGBColorSpace,
  type Vector3
} from "three";
import type {Stage} from "../../../render/stage";
import type {SliceDrawing} from "../../layout/slice";
import type {MoleculeScene} from "./MoleculeScene";

/** How many pixels the picture has for each sample, so that the contour lines are drawn finer than the samples. */
const SCALE = 3;

/** The colour of the contour lines, which show on both the light and the deep ends of the scales. */
const CONTOUR_COLOUR = "rgba(20, 20, 20, 0.55)";

/** How opaque the plane is, so that the atoms behind it can still be seen. */
const OPACITY = 0.92;

/**
 * Draws a field on a plane through the molecule as a colour picture with lines of constant value across it.
 */
export class SlicePlane {
  private mesh: Mesh | null = null;
  private readonly raycaster = new Raycaster();

  constructor(
    private readonly stage: Stage,
    private readonly scene: MoleculeScene,
  ) {}

  /** Forgets the plane, which was taken away along with everything else drawn in the molecule's group. */
  forget(): void {
    this.mesh = null;
  }

  /**
   * @param event where the pointer is
   * @returns where the pointer's ray meets the plane, in the molecule's own frame, and how far from the camera that is, or null if there is
   *     no plane or the ray misses it
   */
  probe(event: { clientX: number; clientY: number }): { point: Vector3; distance: number } | null {
    if (!this.mesh) {
      return null;
    }
    this.raycaster.setFromCamera(this.stage.pointer(event), this.stage.camera);
    const hit = this.raycaster.intersectObject(this.mesh, false)[0];
    return hit ? { point: this.scene.content.worldToLocal(hit.point.clone()), distance: hit.distance } : null;
  }

  /**
   * Draws the plane afresh, taking away the one drawn before.
   *
   * @param drawing what to draw, or null to draw none
   */
  show(drawing: SliceDrawing | null): void {
    const { content } = this.scene;
    if (this.mesh) {
      content.remove(this.mesh);
      this.mesh.geometry.dispose();
      const material = this.mesh.material as MeshBasicMaterial;
      material.map?.dispose();
      material.dispose();
      this.mesh = null;
    }

    if (drawing) {
      const texture = new CanvasTexture(this.picture(drawing));
      texture.colorSpace = SRGBColorSpace;
      texture.minFilter = LinearFilter;
      texture.magFilter = LinearFilter;
      // the first row of the picture is the low end of the second axis, which is where the plane's first corners are
      texture.flipY = false;

      const geometry = new BufferGeometry();
      geometry.setAttribute("position", new Float32BufferAttribute(drawing.corners.flatMap(({ x, y, z }) => [x, y, z]), 3));
      // a sample is the middle of its pixel, so the edges of the plane are half a pixel in from the edges of the picture
      const [u0, v0, u1, v1] = [0.5 / drawing.width, 0.5 / drawing.height, 1 - 0.5 / drawing.width, 1 - 0.5 / drawing.height];
      geometry.setAttribute("uv", new Float32BufferAttribute([u0, v0, u1, v0, u1, v1, u0, v1], 2));
      geometry.setIndex([0, 1, 2, 0, 2, 3]);

      const mesh = new Mesh(geometry, new MeshBasicMaterial({ map: texture, transparent: true, opacity: OPACITY, side: DoubleSide, depthWrite: false, toneMapped: false }));
      mesh.renderOrder = 2;
      content.add(mesh);
      this.mesh = mesh;
    }
    this.stage.requestRender();
  }

  /**
   * @param drawing what to draw
   * @returns a canvas with the samples as a picture, smoothed, and the contour lines over it
   */
  private picture({ pixels, width, height, contours }: SliceDrawing): HTMLCanvasElement {
    const samples = document.createElement("canvas");
    samples.width = width;
    samples.height = height;
    samples.getContext("2d")?.putImageData(new ImageData(new Uint8ClampedArray(pixels), width, height), 0, 0);

    const canvas = document.createElement("canvas");
    canvas.width = width * SCALE;
    canvas.height = height * SCALE;
    const context = canvas.getContext("2d");
    if (!context) {
      return canvas;
    }

    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = "high";
    context.drawImage(samples, 0, 0, canvas.width, canvas.height);

    context.strokeStyle = CONTOUR_COLOUR;
    context.lineWidth = 1;
    context.beginPath();
    // a sample is the middle of its pixels, so the lines are placed by the middle of the samples at each end
    const place = (sample: number) => (sample + 0.5) * SCALE;
    for (let at = 0; at + 3 < contours.length; at += 4) {
      context.moveTo(place(contours[at] as number), place(contours[at + 1] as number));
      context.lineTo(place(contours[at + 2] as number), place(contours[at + 3] as number));
    }
    context.stroke();
    return canvas;
  }
}
