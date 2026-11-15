import type {Vec3} from "../../api/types/structure";
import type {StateIndex} from "./StateIndex";

const ELEMENTS = [
  "H", "He", "Li", "Be", "B", "C", "N", "O", "F", "Ne",
  "Na", "Mg", "Al", "Si", "P", "S", "Cl", "Ar", "K", "Ca",
  "Sc", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn",
  "Ga", "Ge", "As", "Se", "Br", "Kr", "Rb", "Sr", "Y", "Zr",
  "Nb", "Mo", "Tc", "Ru", "Rh", "Pd", "Ag", "Cd", "In", "Sn",
  "Sb", "Te", "I", "Xe", "Cs", "Ba", "La", "Ce", "Pr", "Nd",
  "Pm", "Sm", "Eu", "Gd", "Tb", "Dy", "Ho", "Er", "Tm", "Yb",
  "Lu", "Hf", "Ta", "W", "Re", "Os", "Ir", "Pt", "Au", "Hg",
  "Tl", "Pb", "Bi", "Po", "At", "Rn", "Fr", "Ra", "Ac", "Th",
  "Pa", "U", "Np", "Pu", "Am", "Cm", "Bk", "Cf", "Es", "Fm",
  "Md", "No", "Lr", "Rf", "Db", "Sg", "Bh", "Hs", "Mt", "Ds",
  "Rg", "Cn", "Nh", "Fl", "Mc", "Lv", "Ts", "Og"
];

/** The atoms a torsion about a bond is measured between: one bonded to each end, which with the bond's two atoms make four. */
export interface Reference {
  /** The atom bonded to the bond's first atom. */
  first: string;
  /** The atom bonded to the bond's second atom. */
  second: string;
}

/**
 * Finds the atoms a torsion about a bond could be measured from at one end. The session checks the choice made from
 * these; this is for offering them.
 *
 * @param index the session's atoms and bonds
 * @param bond the bond
 * @param end which end of the bond
 * @returns the atoms bonded to that end other than the one at the far end of the bond, heaviest first
 */
export function referencesAt(index: StateIndex, bond: { first: string; second: string }, end: "first" | "second"): string[] {
  const here = bond[end];
  const far = end === "first" ? bond.second : bond.first;
  const number = (id: string) => ELEMENTS.indexOf(index.atoms.get(id)?.element ?? "") + 1;
  return index
    .neighboursOf(here)
    .filter((id) => id !== far)
    .map((id, place) => ({ id, place }))
    .sort((a, b) => number(b.id) - number(a.id) || a.place - b.place)
    .map(({ id }) => id);
}

/**
 * Chooses the atoms to measure a torsion between when the user has not.
 *
 * @param index the session's atoms and bonds
 * @param bond the bond
 * @returns the heaviest atom bonded to each end, or null if an end has nothing else bonded to it, which leaves no torsion
 */
export function defaultReference(index: StateIndex, bond: { first: string; second: string }): Reference | null {
  const [first] = referencesAt(index, bond, "first");
  const [second] = referencesAt(index, bond, "second");
  return first !== undefined && second !== undefined ? { first, second } : null;
}

/**
 * Measures a torsion: the angle between two planes that meet at a bond, as seen along it.
 * It is positive when, looking along the bond from `b` to `c`, the far atom `d` is clockwise of the near atom `a`.
 *
 * @param a the atom bonded to `b`
 * @param b the first atom of the bond
 * @param c the second atom of the bond
 * @param d the atom bonded to `c`
 * @returns the angle in degrees, above -180 and up to 180
 */
export function torsion(a: Vec3, b: Vec3, c: Vec3, d: Vec3): number {
  const ab = minus(b, a);
  const bc = minus(c, b);
  const cd = minus(d, c);
  const n1 = cross(ab, bc);
  const n2 = cross(bc, cd);
  const along = unit(bc);
  const x = dot(n1, n2);
  const y = dot(cross(n1, n2), along);
  return (Math.atan2(y, x) * 180) / Math.PI;
}

function minus(a: Vec3, b: Vec3): Vec3 {
  return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z };
}

function dot(a: Vec3, b: Vec3): number {
  return a.x * b.x + a.y * b.y + a.z * b.z;
}

function cross(a: Vec3, b: Vec3): Vec3 {
  return { x: a.y * b.z - a.z * b.y, y: a.z * b.x - a.x * b.z, z: a.x * b.y - a.y * b.x };
}

function unit(v: Vec3): Vec3 {
  const length = Math.hypot(v.x, v.y, v.z);
  return { x: v.x / length, y: v.y / length, z: v.z / length };
}
