import { Mesh, Sprite, type Object3D } from "three";

/**
 * Disposes the material of every mesh and sprite under an object (excluding geometry and textures)
 *
 * @param root the object to clear, together with its descendants
 */
export function disposeMaterials(root: Object3D): void {
  root.traverse((object) => {
    if (object instanceof Mesh || object instanceof Sprite) {
      const material = object.material;
      (Array.isArray(material) ? material : [material]).forEach((each) => each.dispose());
    }
  });
}
