import {useObservable} from "../models/observable";
import {type Tool, TOOL_ORDER, type ToolModel} from "../models/ToolModel";
import type {LayoutState, Panel} from "../layoutStore";
import type {SessionPhase} from "../session/useSession";
import {useSessionContext} from "../session/SessionContext";
import {type Segment, Segmented} from "../controls/Segmented";
import {HistoryMenu} from "../panels/HistoryMenu";

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
    value: "edit",
    label: "Edit",
    title: "Add atoms and bonds, move atoms, or delete them. With Click set to Add, clicking an atom adds the chosen element bonded to it, empty space adds one on its own, and dragging an atom moves it (hold it to an axis or plane with X, Y and Z). Set Click to Select to choose atoms and bonds to bond them (B) or delete them (Delete)",
  },
  {
    value: "pan",
    label: "Camera",
    title: "Dragging pans the view and right-dragging orbits it. Scrolling zooms towards the pointer. W, A, S, D fly the camera forward, left, back and right, and E and Q up and down (hold Shift to go faster), as does the fly stick in the corner. Double-click an atom to centre the view on it",
  },
];

export interface ToolbarProps {
  phase: SessionPhase;
  /** Whether there is a structure to optimise. */
  canOptimise: boolean;
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
}

export function Toolbar({ phase, canOptimise, tools, canLock, lockPressed, onToggleLock, layout, onCollapse, canUndo, canRedo }: ToolbarProps) {
  const { optimising, optimise, cancelOptimise, undo, redo, history, goTo, clearHistory } = useSessionContext();
  const { tool } = useObservable(tools);

  return (
    <header className="toolbar">
      <span className="brand">Molecular Studio</span>

      <div className="tool-group" role="group" aria-label="Tools">
        <Segmented label="Tool" value={tool} options={TOOLS.map((each) => ({ ...each, title: `${each.title} (${TOOL_ORDER.indexOf(each.value) + 1})` }))} onChange={(next) => tools.use(next)} />
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
        <button type="button" className="tool" disabled={!canUndo} title="Undo (Ctrl+Z)" onClick={() => void undo()}>
          Undo
        </button>
        <button type="button" className="tool" disabled={!canRedo} title="Redo (Ctrl+Shift+Z)" onClick={() => void redo()}>
          Redo
        </button>
        <HistoryMenu enabled={canUndo || canRedo} onLoad={history} onGoTo={(position, discard) => void goTo(position, discard)} onClear={() => void clearHistory()} />
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
          onClick={() => void (optimising ? cancelOptimise() : optimise())}
        >
          {optimising ? `Stop · step ${optimising.iteration}` : "Optimise"}
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
      <span className="sr-only" role="status">
        {optimising ? "Optimising" : ""}
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
      return "Connected";
    case "wanted":
      return "No molecule";
    default:
      return "Disconnected";
  }
}
