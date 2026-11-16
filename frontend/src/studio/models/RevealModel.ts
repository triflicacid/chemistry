import type {Item} from "./items";

/** Asks the Outliner to show where an item is, without selecting it. */
export class RevealModel {
  private readonly listeners = new Set<(item: Item) => void>();

  /**
   * Hears every request.
   *
   * @param listener who to tell
   * @returns how to stop listening
   */
  subscribe(listener: (item: Item) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * Asks for an item to be opened to and marked in the Outliner.
   *
   * @param item the item to show
   */
  reveal(item: Item): void {
    this.listeners.forEach((listener) => listener(item));
  }
}
