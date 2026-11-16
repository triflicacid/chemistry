import type { PartRef } from "../../render/parts";

export type { PartRef };

/** A position on screen, in client pixels. */
export interface Point {
  x: number;
  y: number;
}

/** Transient emphasis, such as for what the pointer is over. */
export interface Highlights {
  /** The part to emphasise, as the view reported it. */
  hover: PartRef | null;
  /** Atoms to tint; `colour` is 1 or 2. */
  ends: { atom: number; colour: number }[];
}

export type FaultSeverity = "error" | "warning" | "info";

export interface FaultState {
  severity: FaultSeverity;
  atoms: ReadonlySet<number>;
  bonds: ReadonlySet<number>;
}

/** Which atoms and bonds are selected, by index. */
export interface SelectionState {
  atoms: ReadonlySet<number>;
  bonds: ReadonlySet<number>;
}

export interface ViewListener {
  hover?(target: PartRef | null, at: Point): void;
  click?(target: PartRef | null, additive: boolean): void;
}

export interface MoleculeView {
  /** What to put in the page. */
  readonly element: Element;

  /** Hears about the pointer; returns how to stop. */
  listen(listener: ViewListener): () => void;

  /** Marks these atoms and bonds as selected. */
  setSelection(selection: SelectionState): void;

  /** Replaces the highlights. */
  setHighlights(highlights: Highlights): void;

  /** Marks the atoms and bonds of one validation finding, or clears the marks with null. */
  setFaults(faults: FaultState | null): void;

  /** Rows for the hover plaque about what the view knows and the molecule's metadata does not. */
  describe(target: PartRef): [string, string][];

  /** The file type of `exportImage`. */
  readonly imageExtension: "svg" | "png";

  /** The view as an image file. */
  exportImage(): Promise<Blob>;

  /** Puts the camera back where it started. */
  resetView(): void;

  /** Releases listeners. */
  dispose(): void;
}
