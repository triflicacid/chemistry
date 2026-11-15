import type {Vec3} from "../../api/types/structure";
import type {AtomMove} from "../session/messages";
import type {StateIndex} from "./StateIndex";

/** Which end of a bond stays where it is when the bond is made longer or shorter. */
export type Hold = "first" | "second" | "both";

/** What changing a bond's length would do. */
export type LengthEdit =
  | {
      kind: "moves";
      /** The atoms to move and where each goes. */
      moves: AtomMove[];
      /** Whether whole sides of the bond move, rather than only its two ends, which leaves the rest of the molecule behind. */
      sides: boolean;
    }
  | {
      kind: "refused";
      /** Why the bond cannot be changed, for the user. */
      reason: string;
    };

/**
 * Finds the atoms on one side of a bond: those that can be reached from an end without crossing the bond.
 *
 * @param index the session's atoms and bonds
 * @param bond the bond to leave out
 * @param start the end whose side is wanted
 * @returns the atoms on that side, including the end, or null if the other end can be reached too, which is the case
 *          for a bond in a ring, where there is no one side
 */
export function sideOf(index: StateIndex, bond: { id: string; first: string; second: string }, start: string): Set<string> | null {
  const other = start === bond.first ? bond.second : bond.first;
  const side = new Set([start]);
  const pending = [start];

  while (pending.length > 0) {
    const here = pending.pop() as string;
    for (const next of index.bondsOf(here)) {
      if (next.id === bond.id) {
        continue;
      }
      const reached = next.first === here ? next.second : next.first;
      if (reached === other) {
        return null;
      }
      if (!side.has(reached)) {
        side.add(reached);
        pending.push(reached);
      }
    }
  }
  return side;
}

/**
 * Works out how to give a bond a new length.
 *
 * @param index the session's atoms and bonds
 * @param bondId the bond to change
 * @param target the length it should have, in angstroms
 * @param hold which end stays where it is
 * @param locked the atoms that refuse to move
 * @returns the moves to make, or why none can be
 */
export function lengthEdit(index: StateIndex, bondId: string, target: number, hold: Hold, locked: readonly string[]): LengthEdit {
  const bond = index.bonds.get(bondId);
  const first = bond && index.atoms.get(bond.first);
  const second = bond && index.atoms.get(bond.second);
  if (!bond || !first || !second) {
    return { kind: "refused", reason: "That bond is no longer in the molecule." };
  }
  if (!Number.isFinite(target) || target < 0.1 || target > 10) {
    return { kind: "refused", reason: "A bond length must be between 0.1 and 10 Å." };
  }

  const axis = subtract(second.position, first.position);
  const current = Math.hypot(axis.x, axis.y, axis.z);
  if (current < 1e-6) {
    return { kind: "refused", reason: "The two atoms are at the same place, so there is no line to move them along." };
  }

  // how far each end moves along the line from first to second
  const change = target - current;
  const shift = hold === "first" ? { first: 0, second: change } : hold === "second" ? { first: -change, second: 0 } : { first: -change / 2, second: change / 2 };
  const unit = { x: axis.x / current, y: axis.y / current, z: axis.z / current };

  const firstSide = shift.first === 0 ? null : sideOf(index, bond, bond.first);
  const secondSide = shift.second === 0 ? null : sideOf(index, bond, bond.second);
  const sides = (shift.first === 0 || firstSide !== null) && (shift.second === 0 || secondSide !== null);

  const moving = new Map<string, number>();
  if (shift.first !== 0) {
    (firstSide ?? [bond.first]).forEach((id) => moving.set(id, shift.first));
  }
  if (shift.second !== 0) {
    (secondSide ?? [bond.second]).forEach((id) => moving.set(id, shift.second));
  }

  const stuck = [...moving.keys()].filter((id) => locked.includes(id));
  if (stuck.length > 0) {
    return { kind: "refused", reason: `${stuck.map((id) => index.label(id)).join(", ")} ${stuck.length === 1 ? "is" : "are"} locked, so this length cannot change. Hold the other end, or unlock.` };
  }

  const moves: AtomMove[] = [];
  for (const [id, distance] of moving) {
    const atom = index.atoms.get(id);
    if (atom) {
      moves.push({ id, position: { x: atom.position.x + unit.x * distance, y: atom.position.y + unit.y * distance, z: atom.position.z + unit.z * distance } });
    }
  }
  return { kind: "moves", moves, sides };
}

function subtract(a: Vec3, b: Vec3): Vec3 {
  return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z };
}
