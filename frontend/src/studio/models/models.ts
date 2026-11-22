import type {ChemistryStatus} from "../chemistry/useChemistry";
import {CameraModel} from "./CameraModel";
import {FocusModel} from "./FocusModel";
import {HoverModel} from "./HoverModel";
import {PropertiesModel} from "./PropertiesModel";
import {RenderModel} from "./RenderModel";
import {RevealModel} from "./RevealModel";
import type {StateIndex} from "../geometry/StateIndex";
import {SelectionModel} from "./SelectionModel";
import {ToolModel} from "./ToolModel";

/** How a click in the viewport picks: the atom or bond itself, or the whole molecule it is in. */
export type PickScope = "item" | "molecule";

/** What the surfaces share: they write to these and read from them, and never to one another. */
export interface Models {
  selection: SelectionModel;
  hover: HoverModel;
  /** Which tool is in use, and what a move is held to. */
  tools: ToolModel;
  /** Where the camera is, which the viewport reports and the Inspector can change. */
  camera: CameraModel;
  /** Asks the viewport to centre the camera on atoms. */
  focus: FocusModel;
  /** Asks the Outliner to open to an item and mark it. */
  reveal: RevealModel;
  /** How the molecule is drawn. */
  render: RenderModel;
  /** Which calculated properties are shown on it. */
  properties: PropertiesModel;
  /** Looks identifiers up in the state the surfaces are showing; null until a session opens. */
  index: StateIndex | null;
  /** The molecule's chemistry. */
  chemistry: ChemistryStatus;
}

/**
 * Makes the models a workspace needs.
 *
 * @returns a selection and a hover model, with nothing selected, a tool model on the select tool, and a camera model
 *     that knows nothing until a viewport reports
 */
export function createModels(): Pick<Models, "selection" | "hover" | "tools" | "camera" | "focus" | "reveal" | "render" | "properties"> {
  return {
    selection: new SelectionModel(),
    hover: new HoverModel(),
    tools: new ToolModel(),
    camera: new CameraModel(),
    focus: new FocusModel(),
    reveal: new RevealModel(),
    render: new RenderModel(),
    properties: new PropertiesModel(),
  };
}
