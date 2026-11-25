import {Group, MeshBasicMaterial, Vector3} from "three";
import {disposeMaterials} from "../../../render/dispose";
import type {Primitives} from "../../../render/primitives";
import type {Stage} from "../../../render/stage";
import type {HydrogenBondMark} from "../../models/HydrogenBondModel";
import type {MoleculeScene} from "./MoleculeScene";

/** The colour of the bond. */
const BOND_COLOUR = "#d6336c";

/** How long each dash is, and the gap after it, in angstroms. */
const DASH = 0.12;
const GAP = 0.09;

/** How thick the dashes are. */
const WIDTH = 0.025;

/**
 * Draws a hydrogen bond as a dashed line between the hydrogen and the atom it is bonded to.
 */
export class HydrogenBondGuide {
  /** The line and its note, or null while there are none. */
  private marks: Group | null = null;

  constructor(
    private readonly stage: Stage,
    private readonly kit: Primitives,
    private readonly scene: MoleculeScene,
  ) {}

  /** Forgets the line, which was taken away along with everything else drawn in the molecule's group. */
  forget(): void {
    this.marks = null;
  }

  /**
   * Draws the bond afresh from where its atoms are now, taking away the one drawn before.
   *
   * @param mark the bond to draw, or null to draw none
   */
  show(mark: HydrogenBondMark | null): void {
    const { content, atomById } = this.scene;
    if (this.marks) {
      content.remove(this.marks);
      disposeMaterials(this.marks);
      this.marks = null;
    }

    const hydrogen = mark ? atomById.get(mark.hydrogen) : undefined;
    const acceptor = mark ? atomById.get(mark.acceptor) : undefined;
    if (mark && hydrogen && acceptor) {
      const marks = new Group();
      const along = acceptor.at.clone().sub(hydrogen.at);
      const length = along.length();
      const direction = length > 1e-9 ? along.clone().divideScalar(length) : new Vector3(0, 1, 0);

      // the line runs from the surface of one atom to the surface of the other, so that it is not lost inside them
      const from = this.scene.radiusOf(hydrogen);
      const to = length - this.scene.radiusOf(acceptor);
      const material = new MeshBasicMaterial({ color: BOND_COLOUR, depthTest: false });
      for (let start = from; start < to; start += DASH + GAP) {
        const a = hydrogen.at.clone().addScaledVector(direction, start);
        const b = hydrogen.at.clone().addScaledVector(direction, Math.min(start + DASH, to));
        marks.add(this.kit.cylinder(a, b, WIDTH, material));
      }

      const note = this.kit.labels.label(mark.text, BOND_COLOUR, 0.24);
      note.position.copy(hydrogen.at).addScaledVector(along, 0.5);
      note.position.y += 0.25;
      marks.add(note);

      content.add(marks);
      this.marks = marks;
    }
    this.stage.requestRender();
  }
}
