import {Observable} from "./observable";

/** A molecule to write a number beside in the scene. */
export interface NumberedMolecule {
  /** The atoms of the molecule, whose middle the number is written at. */
  atoms: readonly string[];
  /** What to write. */
  text: string;
}

/** The molecules to number in the scene, which the viewport draws while debug mode is on. */
export class MoleculeNumbersModel extends Observable<readonly NumberedMolecule[]> {
  constructor() {
    super([]);
  }

  /**
   * @param molecules the molecules to number, or none to number none
   */
  to(molecules: readonly NumberedMolecule[]): void {
    const held = this.get();
    const same = held.length === molecules.length && held.every((each, i) => each.text === molecules[i]?.text && each.atoms.join(",") === molecules[i]?.atoms.join(","));
    if (!same) {
      this.set(molecules);
    }
  }
}
