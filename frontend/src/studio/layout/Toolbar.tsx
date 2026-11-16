import {useObservable} from "../selection/observable";
import type {Tool, ToolModel} from "../selection/ToolModel";
import type {LayoutState, Panel} from "../layoutStore";
import type {Optimising, SessionPhase} from "../session/useSession";
import {type Segment, Segmented} from "./Segmented";
import {HistoryMenu, type HistoryMenuProps} from "./HistoryMenu";

/** What a left-drag does, one at a time. */
const TOOLS: Segment<Tool>[] = [
  { value: "select", label: "Select", title: "Dragging an atom orbits the camera, like dragging anywhere else" },
  {
    value: "move",
    label: "Move",
    title: "Dragging an atom moves it, and with a molecule selected its handles move the whole molecule. Hold an atom to an axis or plane with X, Y and Z, or the buttons in the viewport",
  },
  { value: "rotate", label: "Rotate", title: "Turn the whole molecule about a pivot, or choose Bonds & angles to turn about a bond or change an angle" },
  {
    value: "pan",
    label: "Pan",
    title: "Dragging pans the view and right-dragging orbits it. Scrolling zooms towards the pointer. Double-click an atom to centre the view on it",
  },
];

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
  onRedo: () => void;
  onLoadHistory: HistoryMenuProps["onLoad"];
  onGoTo: HistoryMenuProps["onGoTo"];
  onClearHistory: HistoryMenuProps["onClear"];
}

export function Toolbar({ phase, optimising, canOptimise, onOptimise, onCancelOptimise, tools, canLock, lockPressed, onToggleLock, layout, onCollapse, canUndo, canRedo, saved, strain, onUndo, onRedo, onLoadHistory, onGoTo, onClearHistory }: ToolbarProps) {
  const { tool } = useObservable(tools);

  return (
    <header className="toolbar">
      <span className="brand">Molecular Studio</span>

      <div className="tool-group" role="group" aria-label="Tools">
        <Segmented label="Tool" value={tool} options={TOOLS} onChange={(next) => tools.use(next)} />
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
        <HistoryMenu enabled={canUndo || canRedo} onLoad={onLoadHistory} onGoTo={onGoTo} onClear={onClearHistory} />
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
