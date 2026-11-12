import type { MoleculeId } from "./api/types";

export class ViewStore {
  private molecule: MoleculeId | null = null;

  /** The id of the molecule being viewed, or null if none is. */
  get moleculeId(): MoleculeId | null {
    return this.molecule;
  }

  viewMolecule(id: MoleculeId): void {
    this.molecule = id;
  }

  clear(): void {
    this.molecule = null;
  }
}
