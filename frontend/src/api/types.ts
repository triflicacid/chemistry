/** One option as described by `GET /api/options/2d` or `/api/options/3d`. */
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

export interface Vector {
  x: number;
  y: number;
  z: number;
}

/** Where a 3D view's camera is, in the molecule's coordinates. */
export interface CameraPose {
  eye: Vector;
  target: Vector;
  up: Vector;
  field_of_view: number;
}

/** How to draw a 3D structure flat: from the clearest direction, or exactly as `camera` sees it. */
export interface ProjectionRequest {
  mode: "best" | "force";
  conformer?: number;
  camera?: CameraPose;
  options: OptionValues;
}

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

export interface ScenePoint {
  x: number;
  y: number;
}

/** Attribute values are strings, numbers or lists of numbers; names follow SVG's. */
export type SceneAttributes = Record<string, string | number | number[]>;

export interface SceneGradient {
  id: string;
  from: ScenePoint;
  to: ScenePoint;
  stops: { offset: number; colour: string }[];
}

interface SceneBase {
  attributes: SceneAttributes;
}

export type SceneElement =
  | (SceneBase & { type: "line"; from: ScenePoint; to: ScenePoint })
  | (SceneBase & { type: "circle"; centre: ScenePoint; radius: number })
  | (SceneBase & { type: "rect"; corner: ScenePoint; width: number; height: number })
  | (SceneBase & { type: "polygon"; points: ScenePoint[] })
  | (SceneBase & { type: "text"; at: ScenePoint; runs: { text: string; attributes: SceneAttributes | null }[] })
  | (SceneBase & { type: "group"; children: SceneElement[] })
  | (SceneBase & { type: "title" | "description"; text: string });

export interface Scene {
  width: number;
  height: number;
  background: string | null;
  attributes: SceneAttributes;
  gradients: SceneGradient[];
  elements: SceneElement[];
}

/** An atom of a 3D structure. `index` is its place in `Structure3D.sites`. */
export interface Site3D {
  index: number;
  element: string;
  isotope: number;
  /** The molecule atom this is (data-atom), or the atom an implied hydrogen hangs off. */
  atom: number;
  implied: boolean;
  charge: number;
  /** Hex colour. */
  colour: string;
  /** Van der Waals radius in angstroms. */
  radius: number;
}

export interface Connection3D {
  first: number;
  second: number;
  order: "SINGLE" | "DOUBLE" | "TRIPLE" | "QUADRUPLE" | "AROMATIC";
  /** The molecule bond this is (data-bond), or -1 for the bond to an implied hydrogen. */
  bond: number;
}

export interface Vec3 {
  x: number;
  y: number;
  z: number;
}

/** What a conformer's geometry says about itself. Angles are degrees, lengths angstroms. */
export interface Diagnostics3D {
  strain: number;
  lengths: { first: number; second: number; actual: number; ideal: number }[];
  angles: { first: number; middle: number; second: number; actual: number; ideal: number }[];
  torsions: { first: number; second: number; third: number; fourth: number; actual: number; ideal: number }[];
  clashes: { first: number; second: number; distance: number; contact: number }[];
  lone_pairs: { node: number; tip: Vec3 }[];
  /** Each site's share of the deviation; 1 is one term at its tolerance. */
  atom_strain: number[];
}

export interface Conformer3D {
  strain: number;
  /** [x, y, z] in angstroms for each site, centred on the origin. */
  positions: [number, number, number][];
  diagnostics: Diagnostics3D;
}

/** A molecule in 3D, as sent by `GET /api/molecules/:id/structure/3d`. */
export interface Structure3D {
  sites: Site3D[];
  connections: Connection3D[];
  /** How far a term may stray from ideal before it counts as strained. */
  tolerances: { length: number; angle: number; torsion: number; clash: number };
  /** The shapes found, least strained first. */
  conformers: Conformer3D[];
}
