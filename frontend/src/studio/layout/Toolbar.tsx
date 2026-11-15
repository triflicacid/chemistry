import type { LayoutState, Panel } from "../store";
import type { SessionPhase } from "../session/useSession";

export interface ToolbarProps {
  phase: SessionPhase;
  layout: LayoutState;
  onCollapse: (panel: Panel, collapsed: boolean) => void;
}

const WAITING = "Not built yet";

export function Toolbar({ phase, layout, onCollapse }: ToolbarProps) {
  return (
    <header className="toolbar">
      <span className="brand">Molecular Studio</span>

      <div className="tool-group" role="group" aria-label="Tools">
        <button type="button" className="tool" disabled title={WAITING}>
          Select
        </button>
        <button type="button" className="tool" disabled title={WAITING}>
          Move
        </button>
        <button type="button" className="tool" disabled title={WAITING}>
          Rotate
        </button>
      </div>

      <div className="tool-group" role="group" aria-label="Edits">
        <button type="button" className="tool" disabled title={WAITING}>
          Undo
        </button>
        <button type="button" className="tool" disabled title={WAITING}>
          Redo
        </button>
        <button type="button" className="tool" disabled title={WAITING}>
          Save
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
