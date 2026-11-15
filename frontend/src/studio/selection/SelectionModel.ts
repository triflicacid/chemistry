import { type Item, sameItem } from "./items";
import { Observable } from "./observable";

/** What is selected. */
export interface Selection {
  /** In the order they were selected. */
  readonly items: readonly Item[];
  /** The item the Inspector shows and a gizmo will sit on: the one last selected, or null if none is. */
  readonly active: Item | null;
}

const NOTHING: Selection = { items: [], active: null };

/** What is selected, in order, and which of it is active. */
export class SelectionModel extends Observable<Selection> {
  constructor() {
    super(NOTHING);
  }

  /**
   * Selects an item.
   *
   * @param item what to select
   * @param additive false to select only this item; true to add it to the selection, or to take
   *   it out if it is already in
   */
  select(item: Item, additive = false): void {
    const { items, active } = this.get();
    const held = items.some((each) => sameItem(each, item));

    if (!additive) {
      if (held && items.length === 1 && sameItem(active, item)) {
        return;
      }
      this.set({ items: [item], active: item });
    } else if (held) {
      const rest = items.filter((each) => !sameItem(each, item));
      this.set({ items: rest, active: sameItem(active, item) ? (rest[rest.length - 1] ?? null) : active });
    } else {
      this.set({ items: [...items, item], active: item });
    }
  }

  /** Selects nothing. */
  clear(): void {
    if (this.get().items.length > 0) {
      this.set(NOTHING);
    }
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
