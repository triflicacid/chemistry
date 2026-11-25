import {Group, Vector3} from "three";
import {disposeMaterials} from "../../../render/dispose";
import type {Primitives} from "../../../render/primitives";
import type {Stage} from "../../../render/stage";
import type {NumberedMolecule} from "../../models/MoleculeNumbersModel";
import type {MoleculeScene} from "./MoleculeScene";

/** The colour of a number. */
const NUMBER_COLOUR = "#0b5fcc";

/** How tall a number is, in angstroms. */
const HEIGHT = 0.6;

/**
 * Writes a number at the middle of each of some molecules, so that a figure that names molecules by number can be matched to
 * the scene. The numbers sit in the molecule's group so that they move with it.
 */
export class MoleculeNumbers {
  /** The numbers, or null while there are none. */
  private marks: Group | null = null;

  constructor(
    private readonly stage: Stage,
    private readonly kit: Primitives,
    private readonly scene: MoleculeScene,
  ) {}

  /** Forgets the numbers, which were taken away along with everything else drawn in the molecule's group. */
  forget(): void {
    this.marks = null;
  }

  /**
   * Draws the numbers afresh from where the atoms are now, taking away the ones drawn before.
   *
   * @param molecules the molecules to number, whose atoms must be those of the structure now shown
   */
  show(molecules: readonly NumberedMolecule[]): void {
    const { content, atomById } = this.scene;
    if (this.marks) {
      content.remove(this.marks);
      disposeMaterials(this.marks);
      this.marks = null;
    }

    if (molecules.length > 0) {
      const marks = new Group();
      for (const molecule of molecules) {
        const nodes = molecule.atoms.map((id) => atomById.get(id)).filter((node) => node !== undefined);
        if (nodes.length === 0) {
          continue;
        }
        const middle = nodes.reduce((sum, node) => sum.add(node.at), new Vector3()).divideScalar(nodes.length);
        const label = this.kit.labels.label(molecule.text, NUMBER_COLOUR, HEIGHT);
        label.position.copy(middle);
        marks.add(label);
      }
      content.add(marks);
      this.marks = marks;
    }
    this.stage.requestRender();
  }
}
