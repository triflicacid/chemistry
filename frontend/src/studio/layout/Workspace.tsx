import type {ReactNode} from "react";
import type {Activity, SessionPhase, SubmitResult} from "../session/useSession";
import type {GeometryResult, SessionState} from "../session/messages";
import {useSessionContext} from "../session/SessionContext";
import type {Models} from "../models/models";
import type {LayoutState, Panel} from "../layoutStore";
import {AnalysisPanel} from "../panels/AnalysisPanel";
import {Inspector} from "../panels/Inspector";
import {ErrorToast} from "../panels/popups/ErrorToast";
import {OptimiseNotice} from "../panels/popups/OptimiseNotice";
import {PropertiesOptions} from "../panels/PropertiesOptions";
import {RenderOptions} from "../panels/RenderOptions";
import {Outliner} from "../panels/Outliner";
import {SmilesBar} from "../controls/SmilesBar";
import {Splitter} from "../controls/Splitter";
import {Toolbar} from "./Toolbar";
import {Viewport} from "./Viewport";

export interface WorkspaceProps {
  phase: SessionPhase;
  state: SessionState | null;
  geometry: GeometryResult | null;
  models: Models;
  activity: Activity[];
  layout: LayoutState;
  onResize: (panel: Panel, size: number) => void;
  onCollapse: (panel: Panel, collapsed: boolean) => void;
  onSubmitSmiles: (smiles: string) => Promise<SubmitResult>;
  canLock: boolean;
  lockPressed: boolean;
  onToggleLock: () => void;
}

export function Workspace(props: WorkspaceProps) {
  const { phase, state, geometry, models, activity, layout, onResize, onCollapse, onSubmitSmiles, canLock, lockPressed, onToggleLock } = props;
  const { outcome } = useSessionContext();

  // the viewport tracks must be able to shrink to nothing, or the canvas holds the grid open
  const columns = [
    layout.outlinerCollapsed ? "var(--rail)" : `${layout.outliner}px`,
    layout.outlinerCollapsed ? "0" : "var(--split)",
    "minmax(0, 1fr)",
    layout.inspectorCollapsed ? "0" : "var(--split)",
    layout.inspectorCollapsed ? "var(--rail)" : `${layout.inspector}px`,
  ].join(" ");

  const rows = [
    "auto",
    "auto",
    "minmax(0, 1fr)",
    layout.analysisCollapsed ? "0" : "var(--split)",
    layout.analysisCollapsed ? "var(--rail)" : `${layout.analysis}px`,
  ].join(" ");

  return (
    <div className="workspace" style={{ gridTemplateColumns: columns, gridTemplateRows: rows }}>
      <Toolbar
        phase={phase}
        canOptimise={phase.kind === "open" && state !== null}
        tools={models.tools}
        canLock={canLock}
        lockPressed={lockPressed}
        onToggleLock={onToggleLock}
        layout={layout}
        onCollapse={onCollapse}
        canUndo={state?.can_undo ?? false}
        canRedo={state?.can_redo ?? false}
      />

      <SmilesBar state={state} onSubmit={onSubmitSmiles} />

      <Side panel="outliner" name="Outliner" collapsed={layout.outlinerCollapsed} onCollapse={onCollapse} footer={<><PropertiesOptions models={models} /><RenderOptions models={models} /></>}>
        <Outliner state={state} models={models} />
      </Side>

      {!layout.outlinerCollapsed && (
        <div className="splitter-cell splitter-left">
          <Splitter panel="outliner" axis="vertical" size={layout.outliner} towards={1} onResize={onResize} />
        </div>
      )}

      <div className="viewport-cell">
        <Viewport phase={phase} state={state} geometry={geometry} models={models} />
        <div className="popups">
          <OptimiseNotice outcome={outcome} />
          <ErrorToast activity={activity} />
        </div>
      </div>

      {!layout.inspectorCollapsed && (
        <div className="splitter-cell splitter-right">
          <Splitter panel="inspector" axis="vertical" size={layout.inspector} towards={-1} onResize={onResize} />
        </div>
      )}

      <Side panel="inspector" name="Inspector" collapsed={layout.inspectorCollapsed} onCollapse={onCollapse}>
        <Inspector state={state} geometry={geometry} models={models} />
      </Side>

      {!layout.analysisCollapsed && (
        <div className="splitter-cell splitter-bottom">
          <Splitter panel="analysis" axis="horizontal" size={layout.analysis} towards={-1} onResize={onResize} />
        </div>
      )}

      <section className={`panel panel-analysis${layout.analysisCollapsed ? " collapsed" : ""}`} aria-label="Activity">
        <Header name="Activity" panel="analysis" collapsed={layout.analysisCollapsed} onCollapse={onCollapse} />
        {!layout.analysisCollapsed && (
          <div className="panel-body">
            <AnalysisPanel activity={activity} state={state} geometry={geometry} models={models} />
          </div>
        )}
      </section>
    </div>
  );
}

interface SideProps {
  panel: Extract<Panel, "outliner" | "inspector">;
  name: string;
  collapsed: boolean;
  onCollapse: (panel: Panel, collapsed: boolean) => void;
  children: ReactNode;
  /** Something pinned to the foot of the panel, which the body does not scroll under. */
  footer?: ReactNode;
}

function Side({ panel, name, collapsed, onCollapse, children, footer }: SideProps) {
  if (collapsed) {
    return (
      <div className={`rail rail-${panel}`}>
        <button type="button" className="rail-open" title={`Show the ${name.toLowerCase()}`} onClick={() => onCollapse(panel, false)}>
          {name}
        </button>
      </div>
    );
  }

  return (
    <section className={`panel panel-${panel}`} aria-label={name}>
      <Header name={name} panel={panel} collapsed={false} onCollapse={onCollapse} />
      <div className="panel-body">{children}</div>
      {footer}
    </section>
  );
}

interface HeaderProps {
  name: string;
  panel: Panel;
  collapsed: boolean;
  onCollapse: (panel: Panel, collapsed: boolean) => void;
}

function Header({ name, panel, collapsed, onCollapse }: HeaderProps) {
  return (
    <div className="panel-header">
      <h2>{name}</h2>
      <button
        type="button"
        className="panel-collapse"
        aria-expanded={!collapsed}
        title={`${collapsed ? "Show" : "Hide"} the ${name.toLowerCase()}`}
        onClick={() => onCollapse(panel, !collapsed)}
      >
        {collapsed ? "▴" : "▾"}
      </button>
    </div>
  );
}
