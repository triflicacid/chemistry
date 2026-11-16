import {type Item, sameItem} from "./items";
import {Observable} from "./observable";

/** What is selected. */
export interface Selection {
  /** In the order they were selected. */
  readonly items: readonly Item[];
  /** The item the Inspector shows and the molecule's handles will sit on: the one last selected, or null if none is. */
  readonly active: Item | null;
}

const NOTHING: Selection = { items: [], active: null };

/** What is selected, in order, and which of it is active. */
export class SelectionModel extends Observable<Selection> {
  constructor() {
    super(NOTHING);
  }

  /**
   * Selects an item, or deselects it if it is already selected.
   *
   * Clicking what is selected lets go of that item alone and keeps the rest. Clicking what is not
   * selected replaces the selection with it, unless `additive`, which adds it instead.
   *
   * @param item the item that was clicked
   * @param additive whether to add an unselected item to the selection rather than replace it
   */
  select(item: Item, additive = false): void {
    const { items, active } = this.get();

    if (items.some((each) => sameItem(each, item))) {
      const rest = items.filter((each) => !sameItem(each, item));
      this.set({ items: rest, active: sameItem(active, item) ? (rest[rest.length - 1] ?? null) : active });
    } else if (additive) {
      this.set({ items: [...items, item], active: item });
    } else {
      this.set({ items: [item], active: item });
    }
  }

  /**
   * Selects exactly these items, whatever was selected before.
   *
   * @param items what to select, in order; the last becomes the active one
   */
  replace(items: readonly Item[]): void {
    if (items.length === 0) {
      this.clear();
    } else {
      this.set({ items: [...items], active: items[items.length - 1] ?? null });
    }
  }

  /** Selects nothing. */
  clear(): void {
    if (this.get().items.length > 0) {
      this.set(NOTHING);
    }
  }

  /**
   * Takes some items out of the selection.
   *
   * @param doomed says whether an item is to be taken out
   */
  remove(doomed: (item: Item) => boolean): void {
    const { items, active } = this.get();
    const kept = items.filter((item) => !doomed(item));
    if (kept.length === items.length) {
      return;
    }
    this.set({ items: kept, active: active && !doomed(active) ? active : (kept[kept.length - 1] ?? null) });
  }

  /**
   * Drops what no longer exists, as when the session sends a state in which some atoms are gone.
   *
   * @param state anything that can say whether an item still exists
   */
  prune(state: { has(item: Item): boolean }): void {
    const { items, active } = this.get();
    const kept = items.filter((item) => state.has(item));
    if (kept.length === items.length) {
      return;
    }
    this.set({ items: kept, active: active && state.has(active) ? active : (kept[kept.length - 1] ?? null) });
  }
}
