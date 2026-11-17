import type {Trigger} from "./binding";

/** Keybinds */
export const KEYS = {
  /** Chooses the tool in that place in the toolbar's order: 1 is the first. */
  chooseTool: { key: ["1", "2", "3", "4", "5"], mod: false, alt: false, shift: false },
  /** Flips what the tool is set to between its choices. */
  switchChoice: { key: "Tab", mod: false, alt: false, shift: false },
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
  /** Bonds the two selected atoms, with the edit tool. */
  addBond: { key: "b", mod: false, alt: false, shift: false },
  /** Deletes the selected atoms or bonds, with the edit tool. */
  deleteSelection: { key: ["Delete", "Backspace"], mod: false, alt: false },
  /** Turns carrying hydrogens on and off. */
  toggleCarry: { key: "h", mod: false, alt: false, shift: false },
} satisfies Record<string, Trigger>;
