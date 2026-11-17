import {
    type ColorRepresentation,
    CylinderGeometry,
    Mesh,
    MeshBasicMaterial,
    MeshStandardMaterial,
    Quaternion,
    SphereGeometry,
    Vector3,
} from "three";
import {type Finish, roughnessOf} from "./finish";
import {LabelFactory} from "./labels";

/** The direction a unit cylinder's axis lies along before it is turned. */
export const UP = new Vector3(0, 1, 0);

/** The geometry and label textures that atoms, bonds and overlays are built from. */
export class Primitives {
  /** A cylinder of radius 1 and height 1, along the y axis, centred on the origin. */
  readonly cylinderGeometry = new CylinderGeometry(1, 1, 1, 12, 1, false);

  /** A sphere of radius 1 centred on the origin. */
  readonly sphereGeometry = new SphereGeometry(1, 24, 16);

  readonly labels = new LabelFactory();

  /** How shiny the surfaces this kit makes are. */
  finish: Finish = "matte";

  /**
   * Makes the material an atom or a bond is drawn with.
   *
   * @param colour its colour
   * @returns a new opaque material with this kit's current finish
   */
  solid(colour: ColorRepresentation): MeshStandardMaterial {
    return new MeshStandardMaterial({ color: colour, roughness: roughnessOf(this.finish) });
  }

  /**
   * Makes a cylinder that runs from one point to another.
   *
   * @param a one end of the cylinder's axis
   * @param b the other end
   * @param radius the radius of the cylinder
   * @param material what to draw it with
   * @returns a mesh sharing this kit's cylinder geometry, scaled and turned to fit
   */
  cylinder(a: Vector3, b: Vector3, radius: number, material: MeshStandardMaterial | MeshBasicMaterial): Mesh {
    const mesh = new Mesh(this.cylinderGeometry, material);
    const along = b.clone().sub(a);
    const length = along.length();
    mesh.position.copy(a).addScaledVector(along, 0.5);
    if (length > 1e-9) {
      mesh.quaternion.copy(new Quaternion().setFromUnitVectors(UP, along.normalize()));
    }
    mesh.scale.set(radius, Math.max(length, 1e-6), radius);
    return mesh;
  }

  /** Frees the geometry and label textures. */
  dispose(): void {
    this.cylinderGeometry.dispose();
    this.sphereGeometry.dispose();
    this.labels.dispose();
  }
}
