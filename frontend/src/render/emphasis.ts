import { Color, Mesh, MeshStandardMaterial, type Object3D } from "three";
import type { Part } from "./parts";

/** The tint of whatever the pointer is over. */
export const HOVER_TINT = new Color("#ffd33d");

/** The tint of what is selected. */
export const SELECTED_TINT = new Color("#58a6ff");

/** How strongly a tint glows against the part's own colour. */
const TINT_INTENSITY = 0.55;

/**
 * Tints some parts and returns every other part to its own colour.
 *
 * The tint is the lit material's emissive colour.
 *
 * @param parts every part of the drawing
 * @param tints the tint of each part that has one, by the part's object
 */
export function applyTints(parts: readonly Part[], tints: ReadonlyMap<Object3D, Color>): void {
  for (const part of parts) {
    const tint = tints.get(part.object);
    part.object.traverse((object) => {
      if (object instanceof Mesh && object.material instanceof MeshStandardMaterial) {
        if (tint) {
          object.material.emissive.copy(tint);
          object.material.emissiveIntensity = TINT_INTENSITY;
        } else {
          object.material.emissive.setScalar(0);
          object.material.emissiveIntensity = 1;
        }
      }
    });
  }
}
