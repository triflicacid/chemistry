import type { MoleculeId } from "./api/types";

/** A molecule remembered between visits. */
export interface RememberedMolecule {
  /** null if the server has no molecule for the SMILES. */
  id: MoleculeId | null;
  smiles: string;
}

/** localStorage key. */
const KEY = "chemistry.molecule";

export class ViewStore {
  private molecule: RememberedMolecule | null = ViewStore.read();

  get moleculeId(): MoleculeId | null {
    return this.molecule?.id ?? null;
  }

  get remembered(): RememberedMolecule | null {
    return this.molecule;
  }

  rememberMolecule(id: MoleculeId, smiles: string): void {
    this.molecule = { id, smiles };
    ViewStore.write(this.molecule);
  }

  rememberSmiles(smiles: string): void {
    this.molecule = { id: null, smiles };
    ViewStore.write(this.molecule);
  }

  private static read(): RememberedMolecule | null {
    try {
      const stored = localStorage.getItem(KEY);
      if (!stored) {
        return null;
      }
      const molecule = JSON.parse(stored) as Partial<RememberedMolecule>;
      if (typeof molecule.smiles !== "string") {
        return null;
      }
      return { id: typeof molecule.id === "string" ? molecule.id : null, smiles: molecule.smiles };
    } catch {
      return null;
    }
  }

  private static write(molecule: RememberedMolecule | null): void {
    try {
      if (molecule) {
        localStorage.setItem(KEY, JSON.stringify(molecule));
      } else {
        localStorage.removeItem(KEY);
      }
    } catch {
    }
  }
}
