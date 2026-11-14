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
const KEY = "chemistry.input";

const MODE_KEY = "chemistry.mode";

const SMARTS_KEY = "chemistry.smarts";

export type ViewMode = "2d" | "3d";

export class ViewStore {
  private text = ViewStore.read();
  private pattern = ViewStore.readSmarts();
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

  /** What the user last entered: one SMILES string per line. */
  get input(): string {
    return this.text;
  }

  /** The SMARTS pattern the user last entered, or blank. */
  get smarts(): string {
    return this.pattern;
  }

  rememberSmarts(smarts: string): void {
    this.pattern = smarts;
    try {
      localStorage.setItem(SMARTS_KEY, smarts);
    } catch {
    }
  }

  rememberInput(input: string): void {
    this.text = input;
    try {
      localStorage.setItem(KEY, input);
    } catch {
    }
  }

  private static readSmarts(): string {
    try {
      return localStorage.getItem(SMARTS_KEY) ?? "";
    } catch {
      return "";
    }
  }

  private static read(): string {
    try {
      return localStorage.getItem(KEY) ?? "";
    } catch {
      return "";
    }
  }
}
