import { useSyncExternalStore } from "react";

/**
 * A value that tells subscribers when it changes.
 *
 * Values must be replaced, never changed in place!!!
 */
export class Observable<T> {
  private readonly listeners = new Set<() => void>();

  constructor(private value: T) {}

  /** The current value. */
  readonly get = (): T => this.value;

  /**
   * Hears about every change.
   *
   * @param listener who to tell
   * @returns how to stop listening
   */
  readonly subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  /**
   * Replaces the value and tells the listeners.
   *
   * @param next the new value
   */
  protected set(next: T): void {
    this.value = next;
    this.listeners.forEach((listener) => listener());
  }
}

/**
 * Reads an observable in a component, which redraws when it changes.
 *
 * @param observable what to read
 * @returns its current value
 */
export function useObservable<T>(observable: Observable<T>): T {
  return useSyncExternalStore(observable.subscribe, observable.get);
}
