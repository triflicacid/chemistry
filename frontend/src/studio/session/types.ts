import type {OptimiseResult} from "./messages";

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
}

export type SubmitResult =
  | { kind: "replaced" }
  | { kind: "conflict" }
  | { kind: "refused"; message: string };

export type EditResult =
  | { kind: "applied" }
  | { kind: "conflict" }
  | { kind: "refused"; message: string };

/** An optimisation the server is running. */
export interface Optimising {
  iteration: number;
}

export interface OptimisationOutcome {
  at: number;
  status: OptimiseResult["status"];
  strainBefore: number | null;
  strainAfter: number | null;
  /** How many atoms it moved. */
  atoms: number;
  iterations: number;
  message: string | null;
  warnings: string[];
}
