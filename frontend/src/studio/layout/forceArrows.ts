import type {Vec3} from "../../api/types/structure";
import type {ForcesResult} from "../session/messages";

/** How many kilocalories per mole there are in a hartree. */
export const KCAL_PER_HARTREE = 627.5095;

/** How many angstroms there are in a bohr. */
export const ANGSTROM_PER_BOHR = 0.529177210903;

/** How long the arrow on the atom with the largest force is when the scale is 1, in angstroms. */
export const ATOM_ARROW = 1.5;

/** How long the arrow of the molecule with the largest total force is when the scale is 1, in angstroms. */
export const NET_ARROW = 2.2;

/** How long the arrow of the molecule with the largest torque is when the scale is 1, in angstroms. */
export const TORQUE_ARROW = 1.6;

/** A force less than this share of the largest is not worth an arrow, which would be no more than a speck. */
export const NEGLIGIBLE = 0.03;

/**
 * @param force a force in hartree per bohr
 * @returns it in kilocalories per mole per angstrom, which is the unit people have a feel for
 */
export function toKcalPerAngstrom(force: number): number {
  return (force * KCAL_PER_HARTREE) / ANGSTROM_PER_BOHR;
}

/**
 * @param vector a vector
 * @returns how long it is
 */
export function magnitude(vector: Vec3): number {
  return Math.hypot(vector.x, vector.y, vector.z);
}

/** The largest of each kind of vector in a result, which the arrows are drawn in proportion to. */
export interface Largest {
  /** The largest force on an atom, in hartree per bohr. */
  atom: number;
  /** The largest total force on a molecule, in hartree per bohr. */
  net: number;
  /** The largest torque on a molecule, in hartree. */
  torque: number;
}

/**
 * @param result what the server said of the forces
 * @returns the largest of each kind of vector in it, zero where there are none
 */
export function largest(result: ForcesResult): Largest {
  return {
    atom: Math.max(0, ...result.atoms.map(({ force }) => magnitude(force))),
    net: Math.max(0, ...result.fragments.map(({ force }) => magnitude(force))),
    torque: Math.max(0, ...result.fragments.map(({ torque }) => magnitude(torque))),
  };
}

/**
 * @param size how big the vector is
 * @param biggest the largest of its kind
 * @param longest how long the arrow of the largest is, in angstroms, at scale 1
 * @param scale the user's multiplier
 * @returns the length in angstroms, or zero if the vector is too small to be worth drawing
 */
export function arrowLength(size: number, biggest: number, longest: number, scale: number): number {
  if (!(biggest > 0) || !Number.isFinite(size) || size / biggest < NEGLIGIBLE) {
    return 0;
  }
  return (size / biggest) * longest * scale;
}
