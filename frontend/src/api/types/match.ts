import type { Scene } from "./scene";

/** One match of a SMARTS pattern, by molecule atom and bond index (the `data-atom` and `data-bond` of a drawing). */
export interface MatchHighlight {
  /** Hex colour. */
  colour: string;
  atoms: number[];
  bonds: number[];
  atom_labels: { index: number; text: string }[];
  bond_labels: { index: number; text: string }[];
  smiles: string | null;
}

/** SMARTS match data for a molecule (3D) */
export interface MatchData {
  /** Whether every match is highlighted at once, rather than one at a time. */
  merged: boolean;
  total: number;
  shown: number;
  /** How much everything outside a match is faded, from 0 to 1. */
  dim: number;
  /** Whether matched atoms are filled with the colour of their match. */
  recolour: boolean;
  labels: boolean;
  matches: MatchHighlight[];
}

export interface MatchResult {
  /** Whether `scenes` is one drawing with every match highlighted, rather than one per match. */
  merged: boolean;
  /** How many matches the pattern has; 0 if it does not match. */
  total: number;
  /** How many of them were drawn. */
  shown: number;
  scenes: Scene[];
  /** SMILES of the fragment each drawn match covers, in order; null where it could not be written. */
  smiles: (string | null)[];
}
