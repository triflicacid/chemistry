/** A position on screen, in client pixels. */
export interface Point {
  x: number;
  y: number;
}

/** An atom or bond under the pointer. `index` is null for parts the drawing added, such as inferred bonds. */
export interface Pick {
  kind: "atom" | "bond";
  index: number | null;
  /** What the drawing recorded about the part, such as `element` or `order` for ones it added. */
  data: Readonly<Record<string, string>>;
  /** Identifies the part to the view that made it; equal for the same part. Not for callers to look inside. */
  handle: unknown;
}

/** Transient emphasis, such as for what the pointer is over. */
export interface Highlights {
  /** The part to emphasise, as the view reported it. */
  hover: Pick | null;
  /** Atoms to tint; `colour` is 1 or 2. */
  ends: { atom: number; colour: number }[];
}

/** Which atoms and bonds are selected, by index. */
export interface SelectionState {
  atoms: ReadonlySet<number>;
  bonds: ReadonlySet<number>;
}

export interface ViewListener {
  hover?(target: Pick | null, at: Point): void;
  click?(target: Pick | null, additive: boolean): void;
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

  /** The file type of `exportImage`. */
  readonly imageExtension: "svg" | "png";

  /** The view as an image file. */
  exportImage(): Promise<Blob>;

  /** Puts the camera back where it started. */
  resetView(): void;

  /** Releases listeners. */
  dispose(): void;
}
