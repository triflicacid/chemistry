/** One option as described by `GET /api/options/2d` or `/api/options/3d`. */
export interface OptionDescriptor {
  name: string;
  label: string;
  description: string;
  type: "boolean" | "number" | "text" | "choice" | "arrangement";
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
  /** Draw only the disconnected parts these molecule atoms are in; every part if absent. */
  atoms?: number[];
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
  systems: SystemInfo[];
  fragments: FragmentInfo[];
}

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

/** A molecule of the server's library. */
export interface LibraryEntry {
  name: string;
  smiles: string;
  classes: string[];
}

/** A SMARTS pattern of the library, with the words that explain it. */
export interface QueryEntry {
  name: string;
  smarts: string;
  classes: string[];
  description: string;
  /** A molecule the pattern matches, or blank if the library gives none. */
  example: string;
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

export interface GeneratedNotation {
  smiles: string;
  canonical_smiles: string;
  /** Whether the canonical SMILES is the stored molecule's, so its metadata applies to it. */
  matches_source: boolean;
  issues: string[];
}

export interface Scene {
  width: number;
  height: number;
  background: string | null;
  attributes: SceneAttributes;
  gradients: SceneGradient[];
  elements: SceneElement[];
  /** Only a projection has this: the SMILES read back from the 3D structure, or null if it could not be read. */
  generated?: GeneratedNotation | null;
}

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

/** What `POST /api/molecules/:id/match/2d` draws. */
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

/** One atom's pi orbital: a lobe either side of the plane its sigma bonds lie in. */
export interface PiOrbital3D {
  /** The site the orbital sits on. */
  node: number;
  /** How many pi electrons the atom lends its system. */
  electrons: number;
  /** Where the lobe one side of the plane reaches; the same side for every orbital of one system. */
  above: Vec3;
  /** Where the lobe on the other side reaches. */
  below: Vec3;
}

/** One bond's share of a pi system's cloud: the lozenge lying over it, on each face of the system. */
export interface PiCloud3D {
  /** The site at one end of the bond. */
  first: number;
  /** The site at the other. */
  second: number;
  /** The middle of the lozenge on one face of the system. */
  above: Vec3;
  /** The middle of the one on the other face. */
  below: Vec3;
}

/**
 * One conjugated pi system, as the two pictures chemistry draws of one: the p orbital standing on
 * each atom, and the cloud they overlap into. A system is aromatic when its bonds can be assigned
 * as alternating single and double bonds, not by its electron count; see SystemInfo.
 */
export interface Conjugation3D {
  /** One orbital per atom of the system, in the molecule's own order. */
  orbitals: PiOrbital3D[];
  /** The cloud over each of the system's bonds, in the molecule's own order. */
  clouds: PiCloud3D[];
  /** The pi electrons counted over the system's atoms. */
  pi_electrons: number;
  aromaticity: "AROMATIC" | "ANTIAROMATIC" | "NONAROMATIC";
}

/** What a conformer's geometry says about itself. Angles are degrees, lengths angstroms. */
export interface Diagnostics3D {
  strain: number;
  lengths: { first: number; second: number; actual: number; ideal: number }[];
  angles: { first: number; middle: number; second: number; actual: number; ideal: number }[];
  torsions: { first: number; second: number; third: number; fourth: number; actual: number; ideal: number }[];
  clashes: { first: number; second: number; distance: number; contact: number }[];
  lone_pairs: { node: number; tip: Vec3 }[];
  /** The pi orbitals of each conjugated system. */
  conjugations: Conjugation3D[];
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
