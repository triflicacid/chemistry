import {
  BufferGeometry,
  DoubleSide,
  Float32BufferAttribute,
  Mesh,
  MeshStandardMaterial,
  Raycaster,
  type Vector3
} from "three";
import type {Surface} from "../../../render/isosurface";
import type {Stage} from "../../../render/stage";
import type {MoleculeScene} from "./MoleculeScene";

/** How the surface is drawn. */
export interface FieldSurfaceOptions {
  /** How opaque the surface is. */
  opacity: number;
  /** The red, green and blue of each vertex, from 0 to 1, or null to draw the whole surface in {@link colour}. */
  colours: Float32Array | null;
  /** The colour of a surface with none for each vertex. */
  colour: string;
}

/** Draws a surface of a scalar field, in one colour or with a colour at each vertex. It sits in the molecule's group so that it moves with it. */
export class FieldSurfaceMesh {
  private mesh: Mesh | null = null;
  private readonly raycaster = new Raycaster();

  constructor(
    private readonly stage: Stage,
    private readonly scene: MoleculeScene,
  ) {}

  forget(): void {
    this.mesh = null;
  }

  /**
   * @param event where the pointer is
   * @returns where the pointer's ray first meets the surface, in the molecule's own frame, or null if there is no surface or the ray misses it
   */
  probe(event: { clientX: number; clientY: number }): Vector3 | null {
    if (!this.mesh) {
      return null;
    }
    this.raycaster.setFromCamera(this.stage.pointer(event), this.stage.camera);
    const hit = this.raycaster.intersectObject(this.mesh, false)[0];
    return hit ? this.scene.content.worldToLocal(hit.point.clone()) : null;
  }

  /**
   * Draws the surface afresh.
   *
   * @param surface the surface, or null to draw none
   * @param options how to draw it
   */
  show(surface: Surface | null, options: FieldSurfaceOptions): void {
    const { content } = this.scene;
    if (this.mesh) {
      content.remove(this.mesh);
      this.mesh.geometry.dispose();
      (this.mesh.material as MeshStandardMaterial).dispose();
      this.mesh = null;
    }

    if (surface && surface.positions.length > 0) {
      const geometry = new BufferGeometry();
      geometry.setAttribute("position", new Float32BufferAttribute(surface.positions, 3));
      geometry.setAttribute("normal", new Float32BufferAttribute(surface.normals, 3));
      if (options.colours) {
        geometry.setAttribute("color", new Float32BufferAttribute(options.colours, 3));
      }
      const mesh = new Mesh(
        geometry,
        new MeshStandardMaterial({
          ...(options.colours ? { vertexColors: true } : { color: options.colour }),
          roughness: 0.5,
          side: DoubleSide,
          transparent: options.opacity < 1,
          opacity: options.opacity,
          depthWrite: options.opacity >= 0.95,
        }),
      );
      mesh.renderOrder = 3;
      content.add(mesh);
      this.mesh = mesh;
    }
    this.stage.requestRender();
  }
}
