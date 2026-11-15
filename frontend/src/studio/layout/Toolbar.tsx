import {useObservable} from "../selection/observable";
import type {ToolModel} from "../selection/ToolModel";
import type {LayoutState, Panel} from "../store";
import type {Optimising, SessionPhase} from "../session/useSession";

export interface ToolbarProps {
  phase: SessionPhase;
  /** The optimisation under way, or null if there is none. */
  optimising: Optimising | null;
  /** Whether there is a structure to optimise. */
  canOptimise: boolean;
  onOptimise: () => void;
  onCancelOptimise: () => void;
  tools: ToolModel;
  /** Whether any atom is selected, which is what a lock applies to. */
  canLock: boolean;
  /** Whether every selected atom is locked, so that the button unlocks them. */
  lockPressed: boolean;
  onToggleLock: () => void;
  layout: LayoutState;
  onCollapse: (panel: Panel, collapsed: boolean) => void;
  canUndo: boolean;
  canRedo: boolean;
  saved: boolean;
  /** How strained the geometry is, ready to show, or null if it has not been worked out. */
  strain: string | null;
  onUndo: () => void;
  onRedo: () => void;}

export function Toolbar({ phase, optimising, canOptimise, onOptimise, onCancelOptimise, tools, canLock, lockPressed, onToggleLock, layout, onCollapse, canUndo, canRedo, saved, strain, onUndo, onRedo }: ToolbarProps) {
  const { tool } = useObservable(tools);

  return (
    <header className="toolbar">
      <span className="brand">Molecular Studio</span>

      <div className="tool-group" role="group" aria-label="Tools">
        <button
          type="button"
          className="tool"
          aria-pressed={tool === "select"}
          title="Dragging an atom orbits the camera, like dragging anywhere else"
          onClick={() => tools.use("select")}
        >
          Select
        </button>
        <button
          type="button"
          className="tool"
          aria-pressed={tool === "move"}
          title="Dragging an atom moves it, and with a molecule selected its handles move the whole molecule. Hold an atom to an axis or plane with X, Y and Z, or the buttons in the viewport"
          onClick={() => tools.use("move")}
        >
          Move
        </button>
        <button
          type="button"
          className="tool"
          aria-pressed={tool === "rotate"}
          title="With a molecule selected, drag a ring to turn the whole molecule about its pivot"
          onClick={() => tools.use("rotate")}
        >
          Rotate
        </button>
        <button
          type="button"
          className="tool"
          aria-pressed={tool === "pan"}
          title="Dragging pans the view and right-dragging orbits it. Scrolling zooms towards the pointer. Double-click an atom to centre the view on it"
          onClick={() => tools.use("pan")}
        >
          Pan
        </button>
        <button
          type="button"
          className="tool"
          disabled={!canLock}
          aria-pressed={lockPressed}
          title="Lock the selected atoms so they cannot be moved, or unlock them (L)"
          onClick={onToggleLock}
        >
          Lock
        </button>
      </div>

      <div className="tool-group" role="group" aria-label="Edits">
        <button type="button" className="tool" disabled={!canUndo} title="Undo (Ctrl+Z)" onClick={onUndo}>
          Undo
        </button>
        <button type="button" className="tool" disabled={!canRedo} title="Redo (Ctrl+Shift+Z)" onClick={onRedo}>
          Redo
        </button>
        <button
          type="button"
          className="tool"
          disabled={!optimising && !canOptimise}
          aria-pressed={optimising !== null}
          title={
            optimising
              ? "Stop optimising. The structure is left as it was"
              : "Relax the geometry to lower its strain, keeping its stereochemistry and holding locked atoms where they are. One undo takes it back"
          }
          onClick={optimising ? onCancelOptimise : onOptimise}
        >
          {optimising ? "Stop" : "Optimise"}
        </button>
      </div>

      <div className="tool-group tool-group-end" role="group" aria-label="Panels">
        <PanelToggle panel="outliner" name="Outliner" collapsed={layout.outlinerCollapsed} onCollapse={onCollapse} />
        <PanelToggle panel="inspector" name="Inspector" collapsed={layout.inspectorCollapsed} onCollapse={onCollapse} />
        <PanelToggle panel="analysis" name="Activity" collapsed={layout.analysisCollapsed} onCollapse={onCollapse} />
      </div>

      <span className={`status status-${phase.kind}`} role="status">
        {describe(phase)}
        {phase.kind === "open" && optimising ? ` · optimising, step ${optimising.iteration}` : ""}
        {phase.kind === "open" && strain ? ` · ${strain}` : ""}
        {phase.kind === "open" && saved ? " · edits saved" : ""}
      </span>
    </header>
  );
}

interface PanelToggleProps {
  panel: Panel;
  name: string;
  collapsed: boolean;
  onCollapse: (panel: Panel, collapsed: boolean) => void;
}

function PanelToggle({ panel, name, collapsed, onCollapse }: PanelToggleProps) {
  return (
    <button
      type="button"
      className="tool"
      aria-pressed={!collapsed}
      title={`${collapsed ? "Show" : "Hide"} the ${name.toLowerCase()}`}
      onClick={() => onCollapse(panel, !collapsed)}
    >
      {name}
    </button>
  );
}

function describe(phase: SessionPhase): string {
  switch (phase.kind) {
    case "connecting":
      return "Connecting";
    case "opening":
      return "Opening";
    case "open":
      return "Ready";
    case "wanted":
      return "No molecule";
    default:
      return "Disconnected";
  }
}
