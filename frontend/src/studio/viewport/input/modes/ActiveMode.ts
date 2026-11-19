import type {InteractionHandler} from "../PointerRouter";
import type {ToolMode} from "./ToolMode";

/** Holds the mode in use and passes the pointer to it. */
export class ActiveMode implements InteractionHandler {
  readonly swallows = false;

  /**
   * @param current the mode in use
   */
  constructor(private current: ToolMode) {}

  /** The mode in use. */
  get mode(): ToolMode {
    return this.current;
  }

  /**
   * Puts the mode in use down and takes up another.
   *
   * @param next the mode to use; nothing happens if it is the one in use
   */
  switchTo(next: ToolMode): void {
    if (next !== this.current) {
      this.current.exit();
      this.current = next;
      next.enter();
    }
  }

  hitTest(event: PointerEvent): boolean {
    return this.current.hitTest(event);
  }

  press(event: PointerEvent): boolean {
    return this.current.press(event);
  }

  move(event: PointerEvent): void {
    this.current.move?.(event);
  }

  release(event: PointerEvent): void {
    this.current.release?.(event);
  }

  cancel(): void {
    this.current.cancel?.();
  }

  hover(event: PointerEvent, over: boolean): void {
    this.current.hover?.(event, over);
  }

  cursor(event: PointerEvent): string | null {
    return this.current.cursor?.(event) ?? null;
  }

  leave(): void {
    this.current.leave?.();
  }
}
