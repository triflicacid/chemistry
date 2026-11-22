import type {Vec3} from "../../api/types/structure";
import type {ScanBond, ScanPoint, SessionState} from "../session/messages";
import {KCAL_PER_HARTREE} from "./forceArrows";

/**
 * @param hartree an energy in hartree
 * @returns it in kilocalories per mole
 */
export function toKcal(hartree: number): number {
  return hartree * KCAL_PER_HARTREE;
}

/**
 * @param point one point of a scan
 * @param withOthers whether to count the other molecules, where the scan has any
 * @returns the energy to plot, in hartree
 */
export function energyAt(point: ScanPoint, withOthers: boolean): number {
  return withOthers && point.energy_with_others !== null ? point.energy_with_others : point.energy;
}

/**
 * @param state the session
 * @returns where every atom is, by atom
 */
export function positionsOf(state: SessionState): Map<string, Vec3> {
  return new Map(state.atoms.map((atom) => [atom.id, atom.position]));
}

/**
 * A scan of a bond moves one molecule, the acceptor's, and puts it where the others were when the scan was made. Scrubbing
 * along the bond moves only that molecule, so the scan is still good after that. Anything else that moves, or an atom that is
 * added or taken away, makes it describe a session that is gone. Scrubbing one bond can therefore spoil another, whose molecule
 * that was not the one moved.
 *
 * @param bond a bond of a scan
 * @param state the session now
 * @param before where every atom was when the scan was made
 * @returns whether the scan of that bond still describes the session
 */
export function bondDescribes(bond: ScanBond, state: SessionState, before: ReadonlyMap<string, Vec3>): boolean {
  const moved = new Set((bond.points[0]?.atoms ?? []).map((atom) => atom.id));
  if (state.atoms.length !== before.size || moved.size === 0) {
    return false;
  }

  for (const atom of state.atoms) {
    const was = before.get(atom.id);
    if (!was) {
      return false;
    }
    if (!moved.has(atom.id) && (was.x !== atom.position.x || was.y !== atom.position.y || was.z !== atom.position.z)) {
      return false;
    }
  }
  return true;
}
