import type {Vec3} from "../../api/types/structure";
import type {Hold} from "./bondLength";
import {sideOf} from "./bondLength";
import type {StateIndex} from "./StateIndex";
import {heaviestFirst} from "./torsion";

/** The two atoms an angle at an atom is measured between, each bonded to it. */
export interface AnglePair {
  from: string;
  to: string;
}

/**
 * Chooses the two atoms to measure an angle between when the user has not.
 *
 * @param index the session's atoms and bonds
 * @param centre the atom the angle is at
 * @returns the two heaviest atoms bonded to it, or null if it has fewer than two, which leaves no angle
 */
export function defaultPair(index: StateIndex, centre: string): AnglePair | null {
  const [from, to] = heaviestFirst(index, index.neighboursOf(centre));
  return from !== undefined && to !== undefined ? { from, to } : null;
}

/**
 * Settles which two atoms an angle is measured between, given what the user chose.
 *
 * @param index the session's atoms and bonds
 * @param centre the atom the angle is at
 * @param chosen what the user chose, either of which may be absent or no longer bonded to the atom
 * @returns the user's choices where they still stand and differ, and the heaviest neighbours where they do not, or null
 *          if the atom has fewer than two neighbours
 */
export function choosePair(index: StateIndex, centre: string, chosen: { from?: string; to?: string }): AnglePair | null {
  const neighbours = heaviestFirst(index, index.neighboursOf(centre));
  const from = chosen.from !== undefined && neighbours.includes(chosen.from) ? chosen.from : neighbours[0];
  const to = chosen.to !== undefined && chosen.to !== from && neighbours.includes(chosen.to) ? chosen.to : neighbours.find((id) => id !== from);
  return from !== undefined && to !== undefined ? { from, to } : null;
}

/**
 * Measures an angle.
 *
 * @param from a point
 * @param centre the point the angle is at
 * @param to another point
 * @returns the angle at `centre` between the two directions, in degrees from 0 to 180
 */
export function angleAt(from: Vec3, centre: Vec3, to: Vec3): number {
  const a = { x: from.x - centre.x, y: from.y - centre.y, z: from.z - centre.z };
  const b = { x: to.x - centre.x, y: to.y - centre.y, z: to.z - centre.z };
  const cosine = (a.x * b.x + a.y * b.y + a.z * b.z) / (Math.hypot(a.x, a.y, a.z) * Math.hypot(b.x, b.y, b.z));
  return (Math.acos(Math.max(-1, Math.min(1, cosine))) * 180) / Math.PI;
}

/**
 * Works out the axis an angle opens and closes about: the unit vector at right angles to the plane of its two arms.
 * Where the arms are in a line there is no plane, and any vector at right angles to them will do.
 *
 * @param from the direction of one arm, from the atom the angle is at
 * @param to the direction of the other
 * @returns the unit vector `from` crossed with `to`, which turning `to` away from `from` is a right-handed turn about
 */
export function planeNormal(from: Vec3, to: Vec3): Vec3 {
  const cross = { x: from.y * to.z - from.z * to.y, y: from.z * to.x - from.x * to.z, z: from.x * to.y - from.y * to.x };
  const length = Math.hypot(cross.x, cross.y, cross.z);
  if (length >= 1e-9 * Math.hypot(from.x, from.y, from.z) * Math.hypot(to.x, to.y, to.z)) {
    return { x: cross.x / length, y: cross.y / length, z: cross.z / length };
  }

  // crossing with the axis the arm points least along cannot give a zero vector
  const [ax, ay, az] = [Math.abs(to.x), Math.abs(to.y), Math.abs(to.z)];
  const least = ax <= ay && ax <= az ? { x: 1, y: 0, z: 0 } : ay <= az ? { x: 0, y: 1, z: 0 } : { x: 0, y: 0, z: 1 };
  const other = { x: to.y * least.z - to.z * least.y, y: to.z * least.x - to.x * least.z, z: to.x * least.y - to.y * least.x };
  const size = Math.hypot(other.x, other.y, other.z);
  return { x: other.x / size, y: other.y / size, z: other.z / size };
}

/**
 * Finds the atoms that changing an angle would move, so that a handle is not offered when one of them is locked.
 *
 * @param index the session's atoms and bonds
 * @param centre the atom the angle is at
 * @param pair the two atoms the angle is measured between
 * @param hold which atom stays where it is: `first` is the pair's `from`, `second` its `to`
 * @returns the atoms that would move, or null if a bond that would have to swing is in a ring
 */
export function angleMovers(index: StateIndex, centre: string, pair: AnglePair, hold: Hold): Set<string> | null {
  const moved = new Set<string>();
  for (const [arm, stays] of [[pair.from, "first"], [pair.to, "second"]] as const) {
    if (hold === stays) {
      continue;
    }
    const bond = index.bondsOf(centre).find((each) => each.first === arm || each.second === arm);
    const side = bond && sideOf(index, bond, arm);
    if (!side) {
      return null;
    }
    side.forEach((id) => moved.add(id));
  }
  return moved;
}
