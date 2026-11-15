export interface Vec3 {
  x: number;
  y: number;
  z: number;
}

/** Where a 3D view's camera is, in the molecule's coordinates. */
export interface CameraPose {
  eye: Vec3;
  target: Vec3;
  up: Vec3;
  field_of_view: number;
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
