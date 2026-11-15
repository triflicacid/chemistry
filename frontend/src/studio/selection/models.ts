import { HoverModel } from "./HoverModel";
import type { StateIndex } from "./StateIndex";
import { SelectionModel } from "./SelectionModel";
import { ToolModel } from "./ToolModel";

/** How a click in the viewport picks: the atom or bond itself, or the whole molecule it is in. */
export type PickMode = "element" | "molecule";

/** What the surfaces share: they write to these and read from them, and never to one another. */
export interface Models {
  selection: SelectionModel;
  hover: HoverModel;
  /** Which tool is in use, and what a move is held to. */
  tools: ToolModel;
  /** Looks identifiers up in the state the surfaces are showing; null until a session opens. */
  index: StateIndex | null;
}

/**
 * Makes the models a workspace needs.
 *
 * @returns a selection and a hover model, with nothing selected, and a tool model on the select tool
 */
export function createModels(): Pick<Models, "selection" | "hover" | "tools"> {
  return { selection: new SelectionModel(), hover: new HoverModel(), tools: new ToolModel() };
}
