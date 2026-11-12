/** One option as described by `GET /api/options`. */
export interface OptionDescriptor {
  name: string;
  label: string;
  description: string;
  type: "boolean" | "number" | "choice" | "arrangement";
  default_value: boolean | number | string;
  unit: string | null;
  min: number | null;
  max: number | null;
  choices: { value: string; label: string }[] | null;
  /** This option has no effect while `option` has `value`. */
  ignored_when: { option: string; value: string } | null;
}

/** Option values by name. */
export type OptionValues = Record<string, boolean | number | string>;

/** The server's id for a stored molecule. */
export type MoleculeId = string;

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
}

export interface BondInfo {
  /** The bond's index in the molecule, (data-bond). */
  index: number;
  /** Atom indices. */
  first: number;
  second: number;
  order: string;
  rings: number[];
}

export interface RingInfo {
  index: number;
  size: number;
  /** Atom indices. */
  atoms: number[];
  bonds: number[];
  smiles: string;
  /** Where each of atoms is written in smiles, in the same order. */
  spans: Span[];
}

export interface MoleculeMetadata {
  smiles: string;
  atoms: AtomInfo[];
  bonds: BondInfo[];
  rings: RingInfo[];
}

/** A molecule the server has stored. */
export interface StoredMolecule {
  id: MoleculeId;
  smiles: string;
}
