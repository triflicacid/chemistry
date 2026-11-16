import {Vector3} from "three";
import type {BondOrder} from "../session/messages";

/** The fewest atoms a ring can have. */
export const MIN_RING = 3;

/** The most atoms a ring the ring guide draws has. */
export const MAX_RING = 12;

/** The ring a guide proposes: where it is, how big, and where each of its atoms would go. */
export interface RingLayout {
  centre: Vector3;
  radius: number;
  /** Where each atom would go, in order round the ring, the first being the atom the ring grows from. */
  vertices: Vector3[];
}

/**
 * @param size a number of atoms, which may be out of range
 * @returns the nearest size a ring can have, as a whole number
 */
export function clampRing(size: number): number {
  return Math.min(MAX_RING, Math.max(MIN_RING, Math.round(size)));
}

/**
 * @param order the order of the bonds the ring is made of
 * @returns about how long they are, in angstroms.
 */
export function ringBondLength(order: BondOrder): number {
  return order === "AROMATIC" ? 1.4 : 1.5;
}

/**
 * Works out a regular ring, in a plane, with one of its atoms where an atom already is.
 *
 * @param source where the atom the ring grows from is
 * @param towards the way from that atom to the middle of the ring, a unit vector in the ring's plane
 * @param normal the unit vector at right angles to the ring's plane
 * @param size how many atoms the ring has
 * @param bondLength how long its bonds are, which with the size fixes how big the ring is
 * @returns the ring, whose first vertex is `source`
 */
export function ringLayout(source: Vector3, towards: Vector3, normal: Vector3, size: number, bondLength: number): RingLayout {
  const radius = bondLength / (2 * Math.sin(Math.PI / size));
  const centre = source.clone().addScaledVector(towards, radius);
  const first = towards.clone().negate();
  const across = normal.clone().cross(first);

  const vertices: Vector3[] = [];
  for (let each = 0; each < size; each++) {
    const turn = (2 * Math.PI * each) / size;
    vertices.push(centre.clone().addScaledVector(first, radius * Math.cos(turn)).addScaledVector(across, radius * Math.sin(turn)));
  }
  return { centre, radius, vertices };
}

/**
 * Chooses which way a ring faces by default: into the room an atom's neighbours leave, as seen in the view's plane.
 *
 * @param arms vectors from the atom to each of its neighbours, in the molecule's frame
 * @param right the view's rightwards direction, a unit vector in the same frame
 * @param up the view's upwards direction, a unit vector in the same frame
 * @returns a unit vector in the view's plane, which is rightwards if the neighbours say nothing about it
 */
export function roomFor(arms: Vector3[], right: Vector3, up: Vector3): Vector3 {
  let x = 0;
  let y = 0;
  for (const arm of arms) {
    const length = arm.length();
    if (length > 1e-9) {
      x -= arm.dot(right) / length;
      y -= arm.dot(up) / length;
    }
  }

  return Math.hypot(x, y) < 1e-6 ? right.clone() : right.clone().multiplyScalar(x).addScaledVector(up, y).normalize();
}

/**
 * Turns a drag on the screen into a direction in the view's plane.
 *
 * @param right the view's rightwards direction, a unit vector
 * @param up the view's upwards direction, a unit vector
 * @param dx how far the pointer is to the right of the atom, in pixels
 * @param dy how far the pointer is below the atom, in pixels
 * @returns the direction the pointer is in, a unit vector, or null if it is too close to the atom to say
 */
export function aimedAt(right: Vector3, up: Vector3, dx: number, dy: number): Vector3 | null {
  if (Math.hypot(dx, dy) < 4) {
    return null;
  }
  return right.clone().multiplyScalar(dx).addScaledVector(up, -dy).normalize();
}
