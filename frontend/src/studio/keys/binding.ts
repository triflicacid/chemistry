import { isTyping } from "./typing";

export interface Trigger {
  /** The key, or any of several, compared without regard to case. */
  key: string | readonly string[];
  shift?: boolean;
  alt?: boolean;
  /** Control or command, which stand for one another. */
  mod?: boolean;
  /** Listens in the capture phase, ahead of everything that is not, rather than in the bubble phase. */
  capture?: boolean;
  /** Takes presses that are being typed into a text field, which are otherwise left to the field. */
  allowWhileTyping?: boolean;
}

export interface Binding extends Trigger {
  run(event: KeyboardEvent): void;
}

/**
 * Whether a key press meets a trigger.
 *
 * @param trigger the trigger
 * @param event the key press
 * @returns true if the press is the trigger's
 */
export function matches(trigger: Trigger, event: KeyboardEvent): boolean {
  const key = event.key.toLowerCase();
  const keys = typeof trigger.key === "string" ? [trigger.key] : trigger.key;
  return (
    keys.some((each) => each.toLowerCase() === key) &&
    (trigger.shift === undefined || trigger.shift === event.shiftKey) &&
    (trigger.alt === undefined || trigger.alt === event.altKey) &&
    (trigger.mod === undefined || trigger.mod === (event.ctrlKey || event.metaKey)) &&
    (trigger.allowWhileTyping === true || !isTyping(event))
  );
}

/**
 * Runs every binding of a phase that a key press meets, in order.
 *
 * @param bindings the bindings
 * @param event the key press
 * @param capture which phase the press is being heard in
 */
export function dispatchKey(bindings: readonly Binding[], event: KeyboardEvent, capture: boolean): void {
  for (const binding of bindings) {
    if ((binding.capture ?? false) === capture && matches(binding, event)) {
      binding.run(event);
    }
  }
}
