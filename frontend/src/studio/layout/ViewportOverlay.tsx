import type {PickScope} from "../models/models";
import type {ToolModel, ToolState} from "../models/ToolModel";
import type {GridKind, GridPlaneChoice} from "../viewport/grid";
import {CONSTRAINTS, GRID_PLANES, GRIDS, ROTATE_TARGETS, SPACES} from "./viewportOptions";
import {Segmented} from "../controls/Segmented";

export interface DisplayToolsProps {
  axes: boolean;
  onAxes: (on: boolean) => void;
  grid: GridKind | "off";
  onGrid: (grid: GridKind | "off") => void;
  gridPlane: GridPlaneChoice;
  onGridPlane: (plane: GridPlaneChoice) => void;
  strain: boolean;
  tools: ToolModel;
  debug: boolean;
  onDebug: (on: boolean) => void;
  onScreenshot: () => void;
}

/** The buttons at the top left of the viewport. */
export function DisplayTools({ axes, onAxes, grid, onGrid, gridPlane, onGridPlane, strain, tools, debug, onDebug, onScreenshot }: DisplayToolsProps) {
  return (
    <div className="viewport-tools viewport-tools-left" role="group" aria-label="Display">
      <button type="button" className="tool" aria-pressed={axes} title="Show or hide the x, y and z axes at the orbit anchor. The small triad in the corner is always shown" onClick={() => onAxes(!axes)}>
        Axes
      </button>
      <span className="segmented-captioned">
        <span className="segmented-caption">Grid</span>
        <Segmented label="Grid" value={grid} options={GRIDS} onChange={onGrid} />
      </span>
      {grid === "cartesian" && (
        <span className="segmented-captioned">
          <span className="segmented-caption">Plane</span>
          <Segmented label="Grid plane" value={gridPlane} options={GRID_PLANES} onChange={onGridPlane} />
        </span>
      )}
      <button
        type="button"
        className="tool"
        aria-pressed={strain}
        title="Mark what is strained: a red halo on atoms, red bonds with their lengths, orange angles with their sizes, and a red line between atoms that are too close. Selecting an atom or bond then details its strain"
        onClick={() => tools.showStrain(!strain)}
      >
        Strain
      </button>
      <button
        type="button"
        className="tool"
        aria-pressed={debug}
        title="Draw guides for moving: the axis or plane the selected atom is held to, as arrows or a faint plane"
        onClick={() => onDebug(!debug)}
      >
        Debug
      </button>
      <button type="button" className="tool" title="Save a picture of what the camera sees as a PNG" onClick={onScreenshot}>
        Screenshot
      </button>
    </div>
  );
}

export interface ActionToolsProps {
  tools: ToolModel;
  state: ToolState;
  /** Whether a grid is shown, which snapping needs. */
  gridShown: boolean;
  moleculeSelected: boolean;
  /** Whether the selection has a locked atom in it. */
  blocked: boolean;
  picking: PickScope;
  onPickScope: () => void;
  onFrame: () => void;
  onCentre: () => void;
  onReset: () => void;
}

/** The buttons at the top right of the viewport. */
export function ActionTools({ tools, state, gridShown, moleculeSelected, blocked, picking, onPickScope, onFrame, onCentre, onReset }: ActionToolsProps) {
  const { tool, constraint, space, rotateTarget, carryHydrogens, snap } = state;
  return (
    <div className="viewport-tools viewport-tools-right" role="group" aria-label="Actions">
      {tool === "rotate" && <Segmented label="What to rotate" value={rotateTarget} options={ROTATE_TARGETS} onChange={(next) => tools.rotateTarget(next)} />}
      {(tool === "move" || (tool === "rotate" && rotateTarget === "molecule" && moleculeSelected)) && (
        <Segmented label="Space" value={space} options={SPACES} onChange={(next) => tools.useSpace(next)} />
      )}
      {tool === "move" && (
        <button
          type="button"
          className="tool"
          aria-pressed={carryHydrogens}
          title="When on, moving an atom carries the hydrogens bonded to it by the same amount, so their bonds keep their lengths and angles (H). Nothing is locked"
          onClick={() => tools.carry(!carryHydrogens)}
        >
          Carry H
        </button>
      )}
      {tool === "move" && gridShown && (
        <button
          type="button"
          className="tool"
          aria-pressed={snap}
          title="When on, a dragged atom or move handle snaps to the nearest point of the grid, which is marked. Hold Ctrl to do the opposite while dragging. A polar grid snaps only free drags"
          onClick={() => tools.snapToGrid(!snap)}
        >
          Snap to grid
        </button>
      )}
      {tool === "move" && !moleculeSelected && (
        <Segmented
          label="Move constraint"
          value={constraint}
          options={CONSTRAINTS.map((each) => ({
            ...each,
            disabled: blocked,
            title: blocked ? "A locked atom is selected, so the selection cannot be moved" : each.title,
          }))}
          onChange={(next) => tools.constrain(next)}
        />
      )}
      <button type="button" className="tool" title="Centre the camera on the selection, or on everything if nothing is selected" onClick={onFrame}>
        Frame selection
      </button>
      {tool === "pan" && (
        <>
          <button
            type="button"
            className="tool"
            title="Bring the middle of the molecule to the middle of the view, keeping the angle and distance the camera has"
            onClick={onCentre}
          >
            Centre camera
          </button>
          <button
            type="button"
            className="tool"
            title="Put the camera at the built-in defaults, not at how it was when the session opened: level, at the default angle and field of view, framing the whole molecule"
            onClick={onReset}
          >
            Reset camera
          </button>
        </>
      )}
      {(tool === "select" || tool === "move") && (
        <button
          type="button"
          className="tool"
          aria-pressed={picking === "molecule"}
          title="When on, clicking or hovering an atom or bond picks the whole molecule it belongs to"
          onClick={onPickScope}
        >
          Pick whole molecule
        </button>
      )}
    </div>
  );
}
