import {Vector3} from "three";
import type {Conjugation3D, PiCloud3D, PiOrbital3D, Vec3} from "../../api/types/structure";

/** How far a pi orbital's lobe reaches from its atom, in angstroms. */
const LOBE_LENGTH = 0.75;

/** How far off the plane of a pi system its cloud lies, in angstroms. */
const CLOUD_OFFSET = 0.45;

const TINY = 1e-6;

/** A pi system as the session names it. */
export interface SystemView {
  atoms: readonly string[];
  /** The two atoms each of the system's bonds joins. */
  bonds: readonly (readonly [string, string])[];
  /** Whether to draw the cloud over the bonds. */
  cloud: boolean;
  /** Whether to write what the system amounts to in its middle. */
  label: boolean;
  piElectrons: number;
  aromaticity: Conjugation3D["aromaticity"];
}

/** A pi system as the drawing holds it, whose atoms are found by their position in a list of the atoms drawn. */
export interface SystemShape {
  /** The system's atoms, by position in the atom list. */
  atoms: readonly number[];
  /** The system's bonds, as the positions of the two atoms each joins. */
  bonds: readonly (readonly [number, number])[];
  piElectrons: number;
  aromaticity: Conjugation3D["aromaticity"];
}

/**
 * Stands a pi system's orbitals and cloud up on atoms where they are now.
 *
 * @param system the system
 * @param at where an atom is, by position in the atom list
 * @param neighbours the atoms bonded to an atom, by position in the atom list; hydrogens count, since they are sigma bonds
 * @returns the system's orbitals and cloud, in the form the shared overlay drawing takes
 */
export function conjugationOf(
  system: SystemShape,
  at: (index: number) => Vector3,
  neighbours: (index: number) => readonly number[],
): Conjugation3D {
  const normals = new Map<number, Vector3>();
  const orbitals: PiOrbital3D[] = [];
  // the server tells each atom's share, which the shape of a lobe grows with; the mean over the system stands in for it
  const electrons = Math.max(1, Math.round(system.piElectrons / Math.max(1, system.atoms.length)));
  let reference: Vector3 | null = null;

  for (const node of system.atoms) {
    let normal = normalAt(node, at, neighbours);
    if (!normal) {
      continue;
    }
    if (!reference) {
      reference = normal;
    } else if (normal.dot(reference) < 0) {
      // every orbital of one system points the same way
      normal = normal.negate();
    }
    const centre = at(node);
    const lobe = normal.clone().multiplyScalar(LOBE_LENGTH);
    normals.set(node, normal);
    orbitals.push({ node, electrons, above: vec(centre.clone().add(lobe)), below: vec(centre.clone().sub(lobe)) });
  }

  const clouds: PiCloud3D[] = [];
  for (const [first, second] of system.bonds) {
    const a = normals.get(first);
    const b = normals.get(second);
    if (!a || !b) {
      continue;
    }
    const offset = a.clone().add(b).normalize().multiplyScalar(CLOUD_OFFSET);
    const middle = at(first).add(at(second)).multiplyScalar(0.5);
    clouds.push({ first, second, above: vec(middle.clone().add(offset)), below: vec(middle.clone().sub(offset)) });
  }

  return { orbitals, clouds, pi_electrons: system.piElectrons, aromaticity: system.aromaticity };
}

/** Fits a plane to the bonds at an atom and takes its unit normal, or null if its bonds give no plane. */
function normalAt(node: number, at: (index: number) => Vector3, neighbours: (index: number) => readonly number[]): Vector3 | null {
  const around = neighbours(node);
  const centre = at(node);
  const normal = new Vector3();
  for (let first = 0; first < around.length; first++) {
    for (let second = first + 1; second < around.length; second++) {
      const pair = at(around[first]!).sub(centre).cross(at(around[second]!).sub(centre));
      if (pair.dot(normal) < 0) {
        normal.sub(pair);
      } else {
        normal.add(pair);
      }
    }
  }
  return normal.length() > TINY ? normal.normalize() : null;
}

function vec(point: Vector3): Vec3 {
  return { x: point.x, y: point.y, z: point.z };
}
