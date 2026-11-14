import type { MoleculeId } from "./api/types";

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
const PANELS_KEY = "chemistry.panels";

const SMARTS_KEY = "chemistry.smarts";

export type ViewMode = "2d" | "3d";

export interface PanelState {
  id: MoleculeId;
  smiles: string;
  mode: ViewMode;
}

export class ViewStore {
  private states = ViewStore.readPanels();
  private pattern = ViewStore.readSmarts();

  /** The panels the page last showed, in the order they were in. */
  get panels(): PanelState[] {
    return this.states;
  }

  rememberPanels(panels: PanelState[]): void {
    this.states = panels;
    try {
      localStorage.setItem(PANELS_KEY, JSON.stringify(panels));
    } catch {
    }
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

  private static readSmarts(): string {
    try {
      return localStorage.getItem(SMARTS_KEY) ?? "";
    } catch {
      return "";
    }
  }

  /** Reads back what was stored, keeping only the panels that are still described in full. */
  private static readPanels(): PanelState[] {
    let stored: unknown;
    try {
      stored = JSON.parse(localStorage.getItem(PANELS_KEY) ?? "[]");
    } catch {
      return [];
    }
    return Array.isArray(stored) ? stored.filter(ViewStore.isPanelState) : [];
  }

  private static isPanelState(value: unknown): value is PanelState {
    const state = value as Partial<PanelState> | null;
    return (
      typeof state?.id === "string" &&
      state.id.length > 0 &&
      typeof state.smiles === "string" &&
      state.smiles.length > 0 &&
      (state.mode === "2d" || state.mode === "3d")
    );
  }
}
