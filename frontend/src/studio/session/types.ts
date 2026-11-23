import type {EnergyStep, OptimiseResult} from "./messages";

/** How far the session has got towards being editable. */
export type SessionPhase =
  | { kind: "connecting" }
  | { kind: "opening" }
  | { kind: "open" }
  /** Nothing to open: the URL named none, or the molecule it named is gone. */
  | { kind: "wanted"; message: string }
  | { kind: "failed"; message: string };

/** One line of the activity log. */
export interface Activity {
  at: number;
  message: string;
  failed: boolean;
  /** Whether it says that something asked for cannot be done, which is not an error: a surface of the interaction density of one molecule, say. */
  warning?: boolean;
  /** Whether the message has line breaks and spacing that mean something, as a SMILES error's caret does. */
  preformatted?: boolean;
}

export type SubmitResult =
  | { kind: "replaced" }
  | { kind: "conflict" }
  | { kind: "refused"; message: string };

export type EditResult =
  /** `added` names what an edit of the graph made, when it made anything. */
  | { kind: "applied"; added?: { atoms: string[]; bonds: string[] } }
  | { kind: "conflict" }
  | { kind: "refused"; message: string };

/** An optimisation the server is running. */
export interface Optimising {
  iteration: number;
  /** The value being minimised at each step the server has reported, which is only some of them. */
  history: EnergyStep[];
}

export interface OptimisationOutcome {
  at: number;
  status: OptimiseResult["status"];
  strainBefore: number | null;
  strainAfter: number | null;
  /** How many atoms it moved. */
  atoms: number;
  iterations: number;
  /** The value that was minimised at every step. */
  history: EnergyStep[];
  message: string | null;
  warnings: string[];
}
