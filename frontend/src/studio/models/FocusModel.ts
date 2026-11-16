/** Asks the viewport to bring atoms into view. */
export class FocusModel {
  private readonly listeners = new Set<(atoms: readonly string[]) => void>();

  /**
   * Hears every request.
   *
   * @param listener who to tell
   * @returns how to stop listening
   */
  readonly subscribe = (listener: (atoms: readonly string[]) => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  /**
   * Asks for the camera to centre on atoms.
   *
   * @param atoms the identifiers of the atoms to show
   */
  focus(atoms: readonly string[]): void {
    this.listeners.forEach((listener) => listener(atoms));
  }
}
