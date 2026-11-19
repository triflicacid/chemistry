/** Something in the viewport that a press may belong to, asked in order of precedence. */
export interface InteractionHandler {
  /** Whether a press taken now would be this handler's. */
  hitTest(event: PointerEvent): boolean;
  /** Whether the press stops here. */
  readonly swallows: boolean;
  /** Takes the press, or returns false to offer it to the next handler. */
  press(event: PointerEvent): boolean;
  /** The pointer moved with a button held, after this handler took the press. */
  move?(event: PointerEvent): void;
  /** A button was released, after this handler took the press. */
  release?(event: PointerEvent): void;
  /** What this handler held has been abandoned. Asked of every handler, whether or not it holds anything. */
  cancel?(): void;
  /** The pointer moved with no button held. `over` is whether this handler is the first the pointer is over. Asked of every handler. */
  hover?(event: PointerEvent, over: boolean): void;
  /** The cursor to show while the pointer is over this handler, or null to leave it to the next. */
  cursor?(event: PointerEvent): string | null;
  /** The pointer left the canvas. */
  leave?(): void;
}

/** Hears the canvas's pointer and passes it to the handlers in order of precedence. */
export class PointerRouter {
  /** The handler that took the press now being held, or null while none is. */
  private active: InteractionHandler | null = null;

  /**
   * @param canvas the canvas to listen on
   * @param handlers who to offer pointer events to, the first having precedence
   */
  constructor(
    private readonly canvas: HTMLCanvasElement,
    private readonly handlers: readonly InteractionHandler[],
  ) {
    canvas.addEventListener("pointermove", this.onMove);
    canvas.addEventListener("pointerleave", this.onLeave);
    canvas.addEventListener("pointerdown", this.onDown, { capture: true });
    canvas.addEventListener("pointerup", this.onUp);
    canvas.addEventListener("pointercancel", this.onCancel);
  }

  /** Stops listening. */
  dispose(): void {
    const canvas = this.canvas;
    canvas.removeEventListener("pointermove", this.onMove);
    canvas.removeEventListener("pointerleave", this.onLeave);
    canvas.removeEventListener("pointerdown", this.onDown, { capture: true });
    canvas.removeEventListener("pointerup", this.onUp);
    canvas.removeEventListener("pointercancel", this.onCancel);
    this.active = null;
  }

  private readonly onDown = (event: PointerEvent): void => {
    for (const handler of this.handlers) {
      if (handler.press(event)) {
        if (handler.swallows) {
          event.stopImmediatePropagation();
          event.preventDefault();
        }
        this.active = handler;
        return;
      }
    }
  };

  private readonly onMove = (event: PointerEvent): void => {
    if (event.buttons !== 0) {
      this.active?.move?.(event);
      return;
    }

    const over = this.handlers.filter((handler) => handler.hitTest(event));
    this.handlers.forEach((handler) => handler.hover?.(event, handler === over[0]));
    let cursor: string | null = null;
    for (const handler of over) {
      cursor = handler.cursor?.(event) ?? null;
      if (cursor !== null) {
        break;
      }
    }
    this.canvas.style.cursor = cursor ?? "";
  };

  private readonly onLeave = (): void => {
    this.handlers.forEach((handler) => handler.leave?.());
  };

  private readonly onUp = (event: PointerEvent): void => {
    const held = this.active;
    this.active = null;
    held?.release?.(event);
  };

  private readonly onCancel = (): void => {
    this.active = null;
    this.handlers.forEach((handler) => handler.cancel?.());
  };
}
