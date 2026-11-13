import type { MoleculeId } from "./api/types";

/** A molecule remembered between visits. */
export interface RememberedMolecule {
  /** null if the server has no molecule for the SMILES. */
  id: MoleculeId | null;
  smiles: string;
}

/** The atoms and bonds the user has selected, by index. Not tied to how the molecule is drawn. */
export class Selection {
  private atomSet = new Set<number>();
  private bondSet = new Set<number>();
  private readonly listeners = new Set<() => void>();

  get atoms(): ReadonlySet<number> {
    return this.atomSet;
  }

  get bonds(): ReadonlySet<number> {
    return this.bondSet;
  }

  get empty(): boolean {
    return this.atomSet.size === 0 && this.bondSet.size === 0;
  }

  /** Selects the part, or deselects it if it was selected; `additive` keeps the rest of the selection. */
  toggle(kind: "atom" | "bond", index: number, additive: boolean): void {
    const mine = kind === "atom" ? this.atomSet : this.bondSet;
    const was = mine.has(index);
    if (!additive) {
      this.atomSet = new Set();
      this.bondSet = new Set();
    }
    const target = kind === "atom" ? this.atomSet : this.bondSet;
    if (was && additive) {
      target.delete(index);
    } else {
      target.add(index);
    }
    this.changed();
  }

  clear(): void {
    if (!this.empty) {
      this.atomSet = new Set();
      this.bondSet = new Set();
      this.changed();
    }
  }

  /** Calls `listener` whenever the selection changes; returns how to stop. */
  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private changed(): void {
    this.listeners.forEach((listener) => listener());
  }
}

/** localStorage key. */
const KEY = "chemistry.molecule";

const MODE_KEY = "chemistry.mode";

export type ViewMode = "2d" | "3d";

export class ViewStore {
  private molecule: RememberedMolecule | null = ViewStore.read();
  private viewMode: ViewMode = ViewStore.readMode();

  /** Whether the user last looked at the 2D or the 3D drawing. */
  get mode(): ViewMode {
    return this.viewMode;
  }

  rememberMode(mode: ViewMode): void {
    this.viewMode = mode;
    try {
      localStorage.setItem(MODE_KEY, mode);
    } catch {
    }
  }

  private static readMode(): ViewMode {
    try {
      return localStorage.getItem(MODE_KEY) === "3d" ? "3d" : "2d";
    } catch {
      return "2d";
    }
  }

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
