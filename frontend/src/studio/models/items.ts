/**
 * What kind of thing an item is. A molecule is one disconnected part of the session's graph. A ring is a set of its atoms
 * and bonds that the molecule's chemistry names, which is not drawn as a part of its own.
 */
export type ItemKind = "atom" | "bond" | "molecule" | "ring";

/** Something that can be selected or hovered, named by the identifier the session gave it. */
export interface Item {
  readonly kind: ItemKind;
  /** An atom or bond identifier, the key of a fragment for a molecule, or a ring identifier. */
  readonly id: string;
}

/** The kinds of thing that are drawn and can be picked directly in the viewport. */
export type PartKind = "atom" | "bond";

/** An atom or bond, named by the identifier the session gave it. */
export interface PartItem extends Item {
  readonly kind: PartKind;
}

/** A set of atoms and bonds, by identifier. */
export interface ItemSet {
  readonly atoms: ReadonlySet<string>;
  readonly bonds: ReadonlySet<string>;
}

/** The empty set of items. */
export const NO_ITEMS: ItemSet = { atoms: new Set(), bonds: new Set() };

/** What to tint on the molecule, strongest last. */
export interface Emphasis {
  selected: ItemSet;
  active: ItemSet;
  hover: ItemSet;
  /** Atoms to tint in a colour of their own, as the ends of a hovered bond are; the colour indexes END_COLOURS. */
  ends?: readonly { id: string; colour: number }[];
  /** The side of a bond that a turn leaves where it is. */
  held?: ItemSet;
  /** The atom the held side stays fixed at, which is marked. */
  anchor?: string;
}

/**
 * Whether a selection stands for every molecule of the session, which is the only time the session's one pose is what moves
 * it. A selection of some of them leaves the others where they are, so it can only be moved by moving its atoms.
 *
 * @param items what is selected
 * @param molecules how many molecules the session holds
 * @returns true if every molecule is selected
 */
export function selectsEveryMolecule(items: readonly Item[], molecules: number): boolean {
  return new Set(items.filter((item) => item.kind === "molecule").map((item) => item.id)).size >= Math.max(1, molecules);
}

/**
 * Names an item as a single string, for use as a map key or a React key.
 *
 * @param item the item
 * @returns a string that is equal for equal items
 */
export function keyOf(item: Item): string {
  return `${item.kind}:${item.id}`;
}

/**
 * Whether two items are the same thing.
 *
 * @param a one item, or null for none
 * @param b another
 * @returns true if both are none or both name the same thing
 */
export function sameItem(a: Item | null, b: Item | null): boolean {
  return a === b || (a !== null && b !== null && a.kind === b.kind && a.id === b.id);
}
