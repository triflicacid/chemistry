import { Vector3 } from "three";
import type { BondEnds } from "./bonds";

/**
 * Finds the centre of the ring system each aromatic bond is in.
 *
 * Two fused rings share a centre, the mean of all their atoms, and not one each.
 *
 * @param links the connections drawn; those that are not aromatic are ignored
 * @param point where a site is, by site index
 * @returns the centre of each aromatic connection's system
 */
export function aromaticCentres<L extends BondEnds>(links: L[], point: (index: number) => Vector3): Map<L, Vector3> {
  const aromatic = links.filter((link) => link.order === "AROMATIC");
  const touching = new Map<number, L[]>();
  for (const link of aromatic) {
    touching.set(link.first, [...(touching.get(link.first) ?? []), link]);
    touching.set(link.second, [...(touching.get(link.second) ?? []), link]);
  }

  const centres = new Map<L, Vector3>();
  for (const start of aromatic) {
    if (centres.has(start)) {
      continue;
    }
    const group = new Set<L>([start]);
    const atoms = new Set<number>();
    const queue = [start];
    while (queue.length > 0) {
      const link = queue.pop() as L;
      for (const atom of [link.first, link.second]) {
        atoms.add(atom);
        for (const next of touching.get(atom) ?? []) {
          if (!group.has(next)) {
            group.add(next);
            queue.push(next);
          }
        }
      }
    }
    const centre = new Vector3();
    atoms.forEach((atom) => centre.add(point(atom)));
    centre.divideScalar(atoms.size);
    group.forEach((link) => centres.set(link, centre));
  }
  return centres;
}
