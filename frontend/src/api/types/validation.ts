import type { Span } from "./span";

/** How much a finding ought to stop you using the molecule. */
export type Severity = "error" | "warning" | "info";

/** One thing the checks found in a molecule. */
export interface FindingInfo {
  /** The rule's stable identifier, e.g. "aromatic.system.not-kekulizable". */
  rule: string;
  severity: Severity;
  /** What part of the molecule it is about, e.g. "aromaticity". */
  category: string;
  /** The finding in words. */
  message: string;
  /** What to change about the molecule, or null where the rule asks for no change. */
  hint: string | null;
  /** Atom indices. */
  atoms: number[];
  bonds: number[];
  /** Where those atoms and bonds are written in MoleculeMetadata.smiles. */
  spans: Span[];
}

/** What checking a molecule found, worst first. */
export interface ValidationReport {
  errors: number;
  warnings: number;
  infos: number;
  findings: FindingInfo[];
}
