import type {StateIndex} from "./StateIndex";

/** Which atom of a bond stays where it is when the bond is made longer or shorter, or turned: its first, its second or neither. */
export type Hold = "first" | "second" | "both";

/**
 * Finds the atoms an edit of a bond would move, so that a handle is not offered when one of them is locked.
 * <p>
 * This is for showing what an edit will do. The session works out for itself what an edit moves, and is the authority.
 *
 * @param index the session's atoms and bonds
 * @param bond the bond
 * @param hold which atom stays where it is
 * @param turning whether the bond is to be turned, which a ring bond cannot be, rather than lengthened
 * @returns the atoms that would move, or null if the bond cannot be turned because it is in a ring
 */
export function movers(index: StateIndex, bond: { id: string; first: string; second: string }, hold: Hold, turning: boolean): Set<string> | null {
  const moved = new Set<string>();
  for (const [end, stays] of [[bond.first, "first"], [bond.second, "second"]] as const) {
    if (hold === stays) {
      continue;
    }
    const side = sideOf(index, bond, end);
    if (side === null && turning) {
      return null;
    }
    // a ring bond is lengthened by moving only its atoms
    (side ?? [end]).forEach((id) => moved.add(id));
  }
  return moved;
}

/**
 * Finds the atoms on one side of a bond: those that can be reached from an end without crossing the bond.
 * <p>
 * This is for showing what an edit will do. The session works out for itself what an edit moves, and is the authority.
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
