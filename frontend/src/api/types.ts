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
  /** Atom indices. */
  atoms: number[];
  bonds: number[];
  smiles: string;
  formula: string;
  /** Where each of atoms is written in smiles, in the same order. */
  spans: Span[];
}

/** One unit of a fragmented formula */
export interface UnitInfo {
  span: Span;
  /** How many of the unit there are. */
  count: number;
  /** Indices into MoleculeMetadata.fragments. */
  fragments: number[];
}

export interface FragmentedInfo {
  text: string;
  units: UnitInfo[];
}

export interface FormulaInfo {
  /** e.g. C₆H₁₂O₆. */
  molecular: string;
  /** e.g. CH₂O. */
  empirical: string;
  /** e.g. CH₃CH₂OH. */
  condensed: string;
  /** e.g. Cu²⁺·SO₄²⁻·5H₂O. */
  components: FragmentedInfo;
  /** e.g. CuSO₄·5H₂O. */
  neutral_units: FragmentedInfo;
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
  fragments: FragmentInfo[];
}

/** A molecule the server has stored. */
export interface StoredMolecule {
  id: MoleculeId;
  smiles: string;
  canonical_smiles: string;
}