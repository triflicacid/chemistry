import {
  BufferGeometry,
  DoubleSide,
  Float32BufferAttribute,
  Group,
  Mesh,
  MeshStandardMaterial,
  Raycaster,
  type Vector3
} from "three";
import type {Surface} from "../../../render/isosurface";
import type {Stage} from "../../../render/stage";
import type {MoleculeScene} from "./MoleculeScene";

/** One surface of a field, with its colour. */
export interface SurfacePart {
  surface: Surface;
  /** The red, green and blue of each vertex, from 0 to 1, or null to draw the whole surface in {@link colour}. */
  colours: Float32Array | null;
  /** The colour of a surface with none for each vertex. */
  colour: string;
}

/**
 * Draws surfaces of a scalar field, each in one colour or with a colour at each vertex: one for a density, two for a field with a positive
 * and a negative part. They sit in the molecule's group so that they move with it.
 */
export class FieldSurfaceMesh {
  private marks: Group | null = null;
  private readonly raycaster = new Raycaster();

  constructor(
    private readonly stage: Stage,
    private readonly scene: MoleculeScene,
  ) {}

  /** Forgets the surfaces, which were taken away along with everything else drawn in the molecule's group. */
  forget(): void {
    this.marks = null;
  }

  /**
   * @param event where the pointer is
   * @returns where the pointer's ray first meets a surface, in the molecule's own frame, or null if there are none or the ray misses them
   */
  probe(event: { clientX: number; clientY: number }): Vector3 | null {
    if (!this.marks) {
      return null;
    }
    this.raycaster.setFromCamera(this.stage.pointer(event), this.stage.camera);
    const hit = this.raycaster.intersectObjects(this.marks.children, false)[0];
    return hit ? this.scene.content.worldToLocal(hit.point.clone()) : null;
  }

  /**
   * Draws the surfaces afresh, taking away those drawn before.
   *
   * @param parts the surfaces, or none to draw none
   * @param opacity how opaque they are
   */
  show(parts: readonly SurfacePart[], opacity: number): void {
    const { content } = this.scene;
    if (this.marks) {
      content.remove(this.marks);
      for (const mesh of this.marks.children as Mesh[]) {
        mesh.geometry.dispose();
        (mesh.material as MeshStandardMaterial).dispose();
      }
      this.marks = null;
    }

    const drawn = parts.filter((part) => part.surface.positions.length > 0);
    if (drawn.length > 0) {
      const marks = new Group();
      for (const { surface, colours, colour } of drawn) {
        const geometry = new BufferGeometry();
        geometry.setAttribute("position", new Float32BufferAttribute(surface.positions, 3));
        geometry.setAttribute("normal", new Float32BufferAttribute(surface.normals, 3));
        if (colours) {
          geometry.setAttribute("color", new Float32BufferAttribute(colours, 3));
        }
        const mesh = new Mesh(
          geometry,
          new MeshStandardMaterial({
            ...(colours ? { vertexColors: true } : { color: colour }),
            roughness: 0.5,
            side: DoubleSide,
            transparent: opacity < 1,
            opacity,
            depthWrite: opacity >= 0.95,
          }),
        );
        mesh.renderOrder = 3;
        marks.add(mesh);
      }
      content.add(marks);
      this.marks = marks;
    }
    this.stage.requestRender();
  }
}
