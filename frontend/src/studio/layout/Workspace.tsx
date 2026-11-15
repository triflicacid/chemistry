import type { ReactNode } from "react";
import type { Activity, SessionPhase } from "../session/useSession";
import type { SessionState } from "../session/messages";
import type { Models } from "../selection/models";
import type { LayoutState, Panel } from "../store";
import { AnalysisPanel } from "./AnalysisPanel";
import { Inspector } from "./Inspector";
import { Outliner } from "./Outliner";
import { Splitter } from "./Splitter";
import { Toolbar } from "./Toolbar";
import { Viewport } from "./Viewport";

export interface WorkspaceProps {
  phase: SessionPhase;
  state: SessionState | null;
  models: Models;
  activity: Activity[];
  layout: LayoutState;
  onResize: (panel: Panel, size: number) => void;
  onCollapse: (panel: Panel, collapsed: boolean) => void;
  onOpenSmiles: (smiles: string) => void;
}

export function Workspace(props: WorkspaceProps) {
  const { phase, state, models, activity, layout, onResize, onCollapse, onOpenSmiles } = props;

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
    "minmax(0, 1fr)",
    layout.analysisCollapsed ? "0" : "var(--split)",
    layout.analysisCollapsed ? "var(--rail)" : `${layout.analysis}px`,
  ].join(" ");

  return (
    <div className="workspace" style={{ gridTemplateColumns: columns, gridTemplateRows: rows }}>
      <Toolbar phase={phase} layout={layout} onCollapse={onCollapse} />

      <Side panel="outliner" name="Outliner" collapsed={layout.outlinerCollapsed} onCollapse={onCollapse}>
        <Outliner state={state} models={models} />
      </Side>

      {!layout.outlinerCollapsed && (
        <div className="splitter-cell splitter-left">
          <Splitter panel="outliner" axis="vertical" size={layout.outliner} towards={1} onResize={onResize} />
        </div>
      )}

      <div className="viewport-cell">
        <Viewport phase={phase} state={state} models={models} onOpenSmiles={onOpenSmiles} />
      </div>

      {!layout.inspectorCollapsed && (
        <div className="splitter-cell splitter-right">
          <Splitter panel="inspector" axis="vertical" size={layout.inspector} towards={-1} onResize={onResize} />
        </div>
      )}

      <Side panel="inspector" name="Inspector" collapsed={layout.inspectorCollapsed} onCollapse={onCollapse}>
        <Inspector state={state} models={models} />
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
            <AnalysisPanel activity={activity} />
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
}

function Side({ panel, name, collapsed, onCollapse, children }: SideProps) {
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
