import type { Trigger } from "./binding";

/** Keybinds */
export const KEYS = {
  /** Clears the selection, unless Escape is leaving a text field. */
  clearSelection: { key: "Escape" },
  /** Abandons a drag that is under way, and goes no further. It is heard while typing, as a drag is not typed into. */
  cancelDrag: { key: "Escape", capture: true, allowWhileTyping: true },
  undo: { key: "z", mod: true, shift: false },
  redo: { key: "z", mod: true, shift: true },
  redoAlternative: { key: "y", mod: true },
  /** Locks the selected atoms, or unlocks them if all of them are locked already. */
  toggleLock: { key: "l", mod: false, alt: false },
  /** Holds a move to an axis, and with shift to the plane across it. Shift is the move's own business, so it is not looked at. */
  constrain: { key: ["x", "y", "z"], mod: false, alt: false },
  /** Turns carrying hydrogens on and off. */
  toggleCarry: { key: "h", mod: false, alt: false, shift: false },
} satisfies Record<string, Trigger>;
