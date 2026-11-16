import type { Camera, Object3D, Raycaster, Vector2, Vector3 } from "three";

/** An atom or bond under the pointer. `index` is null for parts the drawing added, such as inferred bonds. */
export interface PartRef {
  kind: "atom" | "bond";
  index: number | null;
  /** What the drawing recorded about the part, such as `element` or `order` for ones it added. */
  data: Readonly<Record<string, string>>;
  /** Identifies the part to the view that made it; equal for the same part. Not for callers to look inside. */
  handle: unknown;
  /** The stable identifier of the part, for a drawing whose parts have one rather than an index. */
  id?: string;
}

/** An atom or bond that can be picked, and the object that draws it. */
export interface Part {
  ref: PartRef;
  object: Object3D;
  /** The molecule atom or bond index, or -1 if the drawing added it. */
  index: number;
}

/** The parts of one drawing that can be picked. */
export class PartRegistry {
  private entries: Part[] = [];

  /** Every registered part, in the order they were added. */
  get parts(): readonly Part[] {
    return this.entries;
  }

  /**
   * Registers an object as an atom or bond.
   *
   * @param kind whether the object draws an atom or a bond
   * @param index the molecule atom or bond index, or null for something the drawing added
   * @param data what the drawing records about the part, for the hover plaque
   * @param object what draws the part; its handle is the object itself
   * @param id the part's stable identifier, if it has one
   * @returns the new part
   */
  add(kind: "atom" | "bond", index: number | null, data: Record<string, string>, object: Object3D, id?: string): Part {
    const part: Part = { ref: { kind, index, data, handle: object, id }, object, index: index ?? -1 };
    object.userData.part = part;
    this.entries.push(part);
    return part;
  }

  /**
   * Marks every descendant of a part's object as belonging to it.
   *
   * @param part a part whose object has children that a ray may strike
   */
  claimChildren(part: Part): void {
    part.object.traverse((object) => {
      object.userData.part = part;
    });
  }

  /**
   * Forgets one part, leaving the others as they were.
   *
   * @param part a part that was registered
   */
  remove(part: Part): void {
    this.entries = this.entries.filter((each) => each !== part);
  }

  /** Forgets every part, ready for the drawing to be built again. */
  clear(): void {
    this.entries = [];
  }

  /**
   * Finds what a ray from the camera through a point on screen strikes first.
   *
   * @param raycaster the caster to use, which is aimed by this call
   * @param camera the camera the ray starts from
   * @param pointer the point, in normalised device coordinates from -1 to 1
   * @returns the nearest part struck and the point where the ray met it, or null if it struck none
   */
  cast(raycaster: Raycaster, camera: Camera, pointer: Vector2): { part: Part; point: Vector3 } | null {
    raycaster.setFromCamera(pointer, camera);
    const hits = raycaster.intersectObjects(
      this.entries.map((part) => part.object),
      true,
    );
    for (const hit of hits) {
      const part = hit.object.userData.part as Part | undefined;
      if (part) {
        return { part, point: hit.point };
      }
    }
    return null;
  }
}
