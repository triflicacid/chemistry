/** What kind of thing an item is. A molecule is one disconnected part of the session's graph. */
export type ItemKind = "atom" | "bond" | "molecule";

/** Something that can be selected or hovered, named by the identifier the session gave it. */
export interface Item {
  readonly kind: ItemKind;
  /** An atom or bond identifier, or the key of a fragment for a molecule. */
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
