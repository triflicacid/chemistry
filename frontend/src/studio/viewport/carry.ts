/** What the carry rule needs to know of an atom. */
export interface CarrySite {
  id: string;
  element: string;
  /** The atoms it is bonded to. */
  neighbours: readonly string[];
}

/**
 * Adds to some atoms the hydrogens that go with them when they are moved.
 *
 * @param ids the atoms being moved
 * @param site looks up an atom by its identifier, or answers {@code undefined} for one that is not there
 * @returns the atoms being moved followed by the hydrogens that go with them, each once, in the order found
 */
export function withTerminalHydrogens(ids: Iterable<string>, site: (id: string) => CarrySite | undefined): string[] {
  const moving = new Set(ids);

  for (const id of [...moving]) {
    const atom = site(id);
    if (!atom || atom.element === "H") {
      continue;
    }

    for (const neighbour of atom.neighbours) {
      const other = site(neighbour);
      if (other && other.element === "H" && other.neighbours.length === 1) {
        moving.add(other.id);
      }
    }
  }

  return [...moving];
}
