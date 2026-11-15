import { type Item, sameItem } from "./items";
import { Observable } from "./observable";

/** What the pointer is over, in the viewport or in a list. */
export class HoverModel extends Observable<Item | null> {
  constructor() {
    super(null);
  }

  /**
   * Sets what is hovered.
   *
   * @param item what the pointer is over, or null if it is over nothing
   */
  hover(item: Item | null): void {
    if (!sameItem(this.get(), item)) {
      this.set(item);
    }
  }

  /**
   * Stops hovering what no longer exists.
   *
   * @param state anything that can say whether an item still exists
   */
  prune(state: { has(item: Item): boolean }): void {
    const item = this.get();
    if (item && !state.has(item)) {
      this.set(null);
    }
  }
}
