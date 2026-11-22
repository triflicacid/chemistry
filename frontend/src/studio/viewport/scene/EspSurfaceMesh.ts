import {
  BufferGeometry,
  DoubleSide,
  Float32BufferAttribute,
  Mesh,
  MeshStandardMaterial,
  Raycaster,
  type Vector3
} from "three";
import type {Stage} from "../../../render/stage";
import {type EspSurface, vertexColours} from "../../layout/esp";
import type {MoleculeScene} from "./MoleculeScene";

/** How the surface is drawn. */
export interface EspDrawOptions {
  /** How opaque the surface is. */
  opacity: number;
  /** The potential, in hartree per elementary charge, at which the colours are as strong as they get. */
  limit: number;
}

/** Draws a surface of the electron density with each vertex coloured by the electrostatic potential there. It sits in the molecule's group so that it moves with it. */
export class EspSurfaceMesh {
  private mesh: Mesh | null = null;
  private readonly raycaster = new Raycaster();

  constructor(
    private readonly stage: Stage,
    private readonly scene: MoleculeScene,
  ) {}

  /** Forgets the surface, which was taken away along with everything else drawn in the molecule's group. */
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
   * Draws the surface afresh, taking away the one drawn before.
   *
   * @param surface the surface with its potential, or null to draw none
   * @param options how to draw it
   */
  show(surface: EspSurface | null, options: EspDrawOptions): void {
    const { content } = this.scene;
    if (this.mesh) {
      content.remove(this.mesh);
      this.mesh.geometry.dispose();
      (this.mesh.material as MeshStandardMaterial).dispose();
      this.mesh = null;
    }

    if (surface && surface.surface.positions.length > 0) {
      const geometry = new BufferGeometry();
      geometry.setAttribute("position", new Float32BufferAttribute(surface.surface.positions, 3));
      geometry.setAttribute("normal", new Float32BufferAttribute(surface.surface.normals, 3));
      geometry.setAttribute("color", new Float32BufferAttribute(vertexColours(surface.values, options.limit), 3));
      const mesh = new Mesh(
        geometry,
        new MeshStandardMaterial({ vertexColors: true, roughness: 0.5, side: DoubleSide, transparent: options.opacity < 1, opacity: options.opacity, depthWrite: options.opacity >= 0.95 }),
      );
      mesh.renderOrder = 3;
      content.add(mesh);
      this.mesh = mesh;
    }
    this.stage.requestRender();
  }
}
