import { useObservable } from "../selection/observable";
import type { ToolModel } from "../selection/ToolModel";
import type { LayoutState, Panel } from "../store";
import type { SessionPhase } from "../session/useSession";

export interface ToolbarProps {
  phase: SessionPhase;
  tools: ToolModel;
  layout: LayoutState;
  onCollapse: (panel: Panel, collapsed: boolean) => void;
  canUndo: boolean;
  canRedo: boolean;
  saved: boolean;
  onUndo: () => void;
  onRedo: () => void;
}

const WAITING = "Not built yet";

export function Toolbar({ phase, tools, layout, onCollapse, canUndo, canRedo, saved, onUndo, onRedo }: ToolbarProps) {
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
          title="Dragging an atom moves it. Hold it to an axis or plane with X, Y and Z, or the buttons in the viewport"
          onClick={() => tools.use("move")}
        >
          Move
        </button>
        <button type="button" className="tool" disabled title={WAITING}>
          Rotate
        </button>
      </div>

      <div className="tool-group" role="group" aria-label="Edits">
        <button type="button" className="tool" disabled={!canUndo} title="Undo (Ctrl+Z)" onClick={onUndo}>
          Undo
        </button>
        <button type="button" className="tool" disabled={!canRedo} title="Redo (Ctrl+Shift+Z)" onClick={onRedo}>
          Redo
        </button>
        <button type="button" className="tool" disabled title={WAITING}>
          Optimise
        </button>
      </div>

      <div className="tool-group tool-group-end" role="group" aria-label="Panels">
        <PanelToggle panel="outliner" name="Outliner" collapsed={layout.outlinerCollapsed} onCollapse={onCollapse} />
        <PanelToggle panel="inspector" name="Inspector" collapsed={layout.inspectorCollapsed} onCollapse={onCollapse} />
        <PanelToggle panel="analysis" name="Activity" collapsed={layout.analysisCollapsed} onCollapse={onCollapse} />
      </div>

      <span className={`status status-${phase.kind}`} role="status">
        {describe(phase)}
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
