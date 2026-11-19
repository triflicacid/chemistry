import { Mesh, MeshBasicMaterial, MeshStandardMaterial, Object3D } from "three";

/** How far a halo reaches past the atom it surrounds, in angstroms. */
export const HALO_PADDING = 0.12;

/** How much farther out each further halo on the same part sits, so that overlapping ones can be told apart. */
export const HALO_GROWTH = 0.08;

/** The radius of the halo round a bond. */
export const BOND_HALO = 0.17;

const HALO_OPACITY = 0.45;

/**
 * Makes the material for a halo:.
 *
 * @param colour a CSS colour
 * @returns a material that does not write depth, so halos never occlude one another or the part inside
 */
export function haloMaterial(colour: string): MeshBasicMaterial {
  return new MeshBasicMaterial({ color: colour, transparent: true, opacity: HALO_OPACITY, depthWrite: false });
}

/**
 * Makes every lit mesh in `object` see-through.
 *
 * @param object the object whose meshes are changed, together with its descendants
 * @param opacity how opaque to leave them; at 1 or more nothing is changed
 */
export function fade(object: Object3D, opacity: number): void {
  if (opacity >= 1) {
    return;
  }
  object.traverse((each) => {
    if (each instanceof Mesh && each.material instanceof MeshStandardMaterial) {
      each.material.transparent = true;
      each.material.opacity = opacity;
    }
  });
}
