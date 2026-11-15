import type { FormulaInfo } from "./formula";

/** A range of characters, [start, end). */
export interface Span {
  start: number;
  end: number;
}

export interface AtomInfo {
  /** The atom's index in the molecule (data-atom). */
  index: number;
  element: string;
  atomic_number: number | null;
  isotope: number | null;
  charge: number;
  hydrogens: number;
  valence: number;
  aromatic: boolean;
  /** Indices into MoleculeMetadata.rings. */
  rings: number[];
  fragment: number;
  /** Where the atom is written in MoleculeMetadata.smiles. */
  span: Span | null;
  /** Where the atom is written in MoleculeMetadata.canonical_smiles. */
  canonical_span: Span | null;
}

export interface BondInfo {
  /** The bond's index in the molecule, (data-bond). */
  index: number;
  /** Atom indices. */
  first: number;
  second: number;
  order: string;
  rings: number[];
  spans: Span[];
  /** The same, in MoleculeMetadata.canonical_smiles. */
  canonical_spans: Span[];
}

export interface RingInfo {
  index: number;
  size: number;
  /** How the ring's bonds are written, which is independent of whether the ring is aromatic. */
  notation: "aromatic" | "mixed" | "plain";
  /**
   * Index into MoleculeMetadata.systems of the pi system this ring belongs to, or null if its atoms
   * cannot all join one. The ring is aromatic exactly when that system is.
   */
  system: number | null;
  /** Atom indices. */
  atoms: number[];
  bonds: number[];
  smiles: string;
  formula: string;
  /** Where each of atoms is written in smiles, in the same order. */
  spans: Span[];
}

/**
 * One conjugated pi system: a ring, or a set of rings fused into a single plane.
 *
 * A system is aromatic when its bonds can be assigned as alternating single and double bonds,
 * which is what `kekulisable` reports. An electron count is not what decides it: Huckel's 4n+2
 * governs a lone ring and no more, and pyrene holds 16 pi electrons while being as aromatic as
 * benzene. `pi_electrons` is still reported, and 4n is what makes a lone ring antiaromatic.
 */
export interface SystemInfo {
  index: number;
  aromaticity: "aromatic" | "antiaromatic" | "nonaromatic";
  /** Whether the system's bonds can be assigned at all; false means it is not a readable structure. */
  kekulisable: boolean;
  /** The pi electrons counted over its atoms. */
  pi_electrons: number;
  /** How many of MoleculeMetadata.rings fused into it. */
  ring_count: number;
  /** Atom indices. */
  atoms: number[];
  bonds: number[];
  /** Indices into MoleculeMetadata.rings. */
  rings: number[];
}

export interface FragmentInfo {
  index: number;
  atoms: number[];
  bonds: number[];
  smiles: string;
  formula: string;
  charge: number;
}

export interface MoleculeMetadata {
  smiles: string;
  canonical_smiles: string;
  formula: FormulaInfo;
  atoms: AtomInfo[];
  bonds: BondInfo[];
  rings: RingInfo[];
  systems: SystemInfo[];
  fragments: FragmentInfo[];
}
