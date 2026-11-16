import type { SessionAtom, SessionBond, SessionFragment, SessionState } from "../session/messages";
import type { Item, ItemSet } from "../models/items";
import type { StudioRing } from "../chemistry/Chemistry";

/**
 * Looks things up in a session state: which molecule an atom is in, what an atom is bonded to,
 * what to call an atom.
 */
export class StateIndex {
  readonly atoms = new Map<string, SessionAtom>();
  readonly bonds = new Map<string, SessionBond>();
  readonly fragments = new Map<string, SessionFragment>();
  readonly rings = new Map<string, StudioRing>();
  private readonly labels = new Map<string, string>();
  private readonly fragmentOfAtom = new Map<string, string>();
  private readonly bondsAt = new Map<string, SessionBond[]>();
  private readonly bondsIn = new Map<string, SessionBond[]>();

  /**
   * @param state the session's state
   * @param rings the molecule's rings, which are known only once the server has read its chemistry
   */
  constructor(readonly state: SessionState, rings: readonly StudioRing[] = []) {
    rings.forEach((ring) => this.rings.set(ring.id, ring));
    state.atoms.forEach((atom, index) => {
      this.atoms.set(atom.id, atom);
      this.labels.set(atom.id, `${atom.element}${index + 1}`);
    });
    for (const fragment of state.fragments) {
      this.fragments.set(fragment.key, fragment);
      fragment.atoms.forEach((id) => this.fragmentOfAtom.set(id, fragment.key));
    }
    for (const bond of state.bonds) {
      this.bonds.set(bond.id, bond);
      for (const end of [bond.first, bond.second]) {
        this.bondsAt.set(end, [...(this.bondsAt.get(end) ?? []), bond]);
      }
      const key = this.fragmentOfAtom.get(bond.first);
      if (key !== undefined) {
        this.bondsIn.set(key, [...(this.bondsIn.get(key) ?? []), bond]);
      }
    }
  }

  /**
   * Names an atom by its element and its place in the molecule, as `O1`, `H2`, `H3`.
   *
   * @param id an atom identifier
   * @returns the name, or `?` for an unknown atom
   */
  label(id: string): string {
    return this.labels.get(id) ?? "?";
  }

  /**
   * Names a bond by the atoms it joins.
   *
   * @param bond the bond
   * @returns the name, such as `C1-O2` for a single bond or `C1=O2` for a double one
   */
  bondLabel(bond: SessionBond): string {
    return `${this.label(bond.first)}${bondSymbol(bond.order)}${this.label(bond.second)}`;
  }

  /**
   * Finds the bonds on an atom.
   *
   * @param id an atom identifier
   * @returns the bonds that have the atom at either end
   */
  bondsOf(id: string): readonly SessionBond[] {
    return this.bondsAt.get(id) ?? [];
  }

  /**
   * Finds the atoms bonded to an atom.
   *
   * @param id an atom identifier
   * @returns the atom at the other end of each of its bonds
   */
  neighboursOf(id: string): string[] {
    return this.bondsOf(id).map((bond) => (bond.first === id ? bond.second : bond.first));
  }

  /**
   * Finds the bonds inside a molecule.
   *
   * @param key a fragment key
   * @returns the bonds joining the molecule's atoms
   */
  bondsInFragment(key: string): readonly SessionBond[] {
    return this.bondsIn.get(key) ?? [];
  }

  /**
   * Finds the molecule an atom or bond is in.
   *
   * @param item the item; a molecule is in itself
   * @returns the fragment key, or null if the item is not in this state
   */
  fragmentOf(item: Item): string | null {
    switch (item.kind) {
      case "atom":
        return this.fragmentOfAtom.get(item.id) ?? null;
      case "bond": {
        const bond = this.bonds.get(item.id);
        return bond ? (this.fragmentOfAtom.get(bond.first) ?? null) : null;
      }
      case "ring": {
        const [first] = this.rings.get(item.id)?.atoms ?? [];
        return first === undefined ? null : (this.fragmentOfAtom.get(first) ?? null);
      }
      default:
        return this.fragments.has(item.id) ? item.id : null;
    }
  }

  /**
   * Whether the state still has what an item names.
   *
   * @param item the item
   * @returns true if it does
   */
  has(item: Item): boolean {
    switch (item.kind) {
      case "atom":
        return this.atoms.has(item.id);
      case "bond":
        return this.bonds.has(item.id);
      case "ring":
        return this.rings.has(item.id);
      default:
        return this.fragments.has(item.id);
    }
  }

  /**
   * Works out which atoms and bonds some items stand for. A molecule stands for everything in it.
   *
   * @param items the items, which may be of any kind
   * @returns every atom and bond they cover, without repeats
   */
  members(items: Iterable<Item>): ItemSet {
    const covered = { atoms: new Set<string>(), bonds: new Set<string>() };
    for (const item of items) {
      if (item.kind === "atom") {
        covered.atoms.add(item.id);
      } else if (item.kind === "bond") {
        covered.bonds.add(item.id);
      } else if (item.kind === "ring") {
        const ring = this.rings.get(item.id);
        ring?.atoms.forEach((id) => covered.atoms.add(id));
        ring?.bonds.forEach((id) => covered.bonds.add(id));
      } else {
        this.fragments.get(item.id)?.atoms.forEach((id) => covered.atoms.add(id));
        this.bondsInFragment(item.id).forEach((bond) => covered.bonds.add(bond.id));
      }
    }
    return covered;
  }

  /**
   * Counts the elements of a molecule, carbon and hydrogen first, as a molecular formula.
   *
   * @param key a fragment key
   * @returns the formula, such as `C2H6O`, or an empty string for an unknown molecule
   */
  formula(key: string): string {
    const counts = new Map<string, number>();
    this.fragments.get(key)?.atoms.forEach((id) => {
      const element = this.atoms.get(id)?.element;
      if (element !== undefined) {
        counts.set(element, (counts.get(element) ?? 0) + 1);
      }
    });

    const rest = [...counts.keys()].filter((element) => element !== "C" && element !== "H").sort();
    return ["C", "H", ...rest]
      .filter((element) => counts.has(element))
      .map((element) => `${element}${counts.get(element) === 1 ? "" : counts.get(element)}`)
      .join("");
  }
}

/**
 * The symbol a bond order is written with in SMILES.
 *
 * @param order the bond's order
 * @returns `-`, `=`, `#`, `$` or `:`
 */
export function bondSymbol(order: SessionBond["order"]): string {
  switch (order) {
    case "DOUBLE":
      return "=";
    case "TRIPLE":
      return "#";
    case "QUADRUPLE":
      return "$";
    case "AROMATIC":
      return ":";
    default:
      return "-";
  }
}
