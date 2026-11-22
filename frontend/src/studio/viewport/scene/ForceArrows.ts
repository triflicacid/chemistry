import {Group, MeshStandardMaterial, Vector3} from "three";
import {disposeMaterials} from "../../../render/dispose";
import type {Primitives} from "../../../render/primitives";
import type {Stage} from "../../../render/stage";
import {arrowLength, ATOM_ARROW, largest, magnitude, NET_ARROW, TORQUE_ARROW} from "../../layout/forceArrows";
import type {ForcesResult} from "../../session/messages";
import type {MoleculeScene} from "./MoleculeScene";

/** The colour of the arrow on an atom. */
export const ATOM_COLOUR = "#0f9d8a";

/** The colour of the arrow for the total force on a molecule. */
export const NET_COLOUR = "#e8590c";

/** The colour of the arrow along the axis a molecule is being turned about. */
export const TORQUE_COLOUR = "#9c36b6";

/** How the arrows are drawn. */
export interface ForceOptions {
  /** A multiple of the usual arrow length. */
  scale: number;
  /** Whether to also draw the total force and the torque on each molecule. */
  net: boolean;
}

/**
 * Draws the forces the other molecules put on each atom as arrows, and optionally the total force and torque on each
 * molecule. The arrows sit in the molecule's group so that they move with it.
 */
export class ForceArrows {
  /** The arrows, or null while there are none. */
  private marks: Group | null = null;

  constructor(
    private readonly stage: Stage,
    private readonly kit: Primitives,
    private readonly scene: MoleculeScene,
  ) {}

  /** Forgets the arrows, which were taken away along with everything else drawn in the molecule's group. */
  forget(): void {
    this.marks = null;
  }

  /**
   * Draws the arrows afresh, taking away the ones drawn before.
   *
   * @param result what the server said of the forces, which must be of the structure now drawn, or null to show none
   * @param options how to draw them
   */
  show(result: ForcesResult | null, options: ForceOptions): void {
    const { content, atomById } = this.scene;
    if (this.marks) {
      content.remove(this.marks);
      disposeMaterials(this.marks);
      this.marks = null;
    }

    if (result && result.status === "complete") {
      const marks = new Group();
      const biggest = largest(result);
      const arrow = (from: Vector3, direction: Vector3, length: number, radius: number, colour: string) => {
        if (length > 0) {
          const material = new MeshStandardMaterial({ color: colour, roughness: 0.5 });
          marks.add(this.kit.arrow(from, from.clone().addScaledVector(direction.normalize(), length), radius, material));
        }
      };

      for (const { id, force } of result.atoms) {
        const node = atomById.get(id);
        const length = arrowLength(magnitude(force), biggest.atom, ATOM_ARROW, options.scale);
        if (node && length > 0) {
          const direction = new Vector3(force.x, force.y, force.z).normalize();
          // the arrow starts at the surface of the atom, so that it is not lost inside it
          const start = node.at.clone().addScaledVector(direction, this.scene.radiusOf(node));
          arrow(start, direction, length, 0.04, ATOM_COLOUR);
        }
      }

      if (options.net) {
        for (const { centre, force, torque } of result.fragments) {
          const middle = new Vector3(centre.x, centre.y, centre.z);
          arrow(middle, new Vector3(force.x, force.y, force.z), arrowLength(magnitude(force), biggest.net, NET_ARROW, options.scale), 0.07, NET_COLOUR);
          arrow(middle, new Vector3(torque.x, torque.y, torque.z), arrowLength(magnitude(torque), biggest.torque, TORQUE_ARROW, options.scale), 0.06, TORQUE_COLOUR);
        }
      }

      content.add(marks);
      this.marks = marks;
    }
    this.stage.requestRender();
  }
}
