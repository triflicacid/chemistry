import type {ReactNode} from "react";
import type {Vec3} from "../../api/types/structure";
import type {
    Activity,
    EditResult,
    OptimisationOutcome,
    Optimising,
    SessionPhase,
    SubmitResult
} from "../session/useSession";
import type {AtomMove, BondEdit, GeometryResult, Pose, SessionState} from "../session/messages";
import type {Models} from "../selection/models";
import type {LayoutState, Panel} from "../store";
import {AnalysisPanel} from "./AnalysisPanel";
import {Inspector} from "./Inspector";
import {OptimiseNotice} from "./OptimiseNotice";
import {Outliner} from "./Outliner";
import {SmilesBar} from "./SmilesBar";
import {Splitter} from "./Splitter";
import {Toolbar} from "./Toolbar";
import {Viewport} from "./Viewport";

export interface WorkspaceProps {
  phase: SessionPhase;
  state: SessionState | null;
  geometry: GeometryResult | null;
  /** How strained the geometry is, ready to show, or null if it has not been worked out. */
  strain: string | null;
  models: Models;
  activity: Activity[];
  layout: LayoutState;
  onResize: (panel: Panel, size: number) => void;
  onCollapse: (panel: Panel, collapsed: boolean) => void;
  onOpenSmiles: (smiles: string) => void;
  onSubmitSmiles: (smiles: string) => Promise<SubmitResult>;
  onMoveAtom: (id: string, position: Vec3) => void;
  onSetBondLength: (bond: string, length: number, hold: string | null) => Promise<EditResult>;
  onSetBondTorsion: (bond: string, from: string, to: string, angle: number, hold: string | null) => Promise<EditResult>;
  onSetAngle: (centre: string, from: string, to: string, angle: number, hold: string | null) => Promise<EditResult>;
  onLockAtoms: (ids: string[], locked: boolean) => void;
  canLock: boolean;
  lockPressed: boolean;
  onToggleLock: () => void;
  onDragAtoms: (moves: AtomMove[]) => void;
  onDropAtoms: (moves: AtomMove[]) => void;
  onCancelDrag: (moves: AtomMove[]) => void;
  onMoveMolecule: (pose: Pose) => void;
  onDragMolecule: (pose: Pose) => void;
  onDropMolecule: (pose: Pose) => void;
  onCancelMoleculeDrag: (from: Pose) => void;
  onDragBond: (edit: BondEdit) => void;
  onDropBond: (edit: BondEdit) => void;
  onCancelBondDrag: () => void;
  onUndo: () => void;
  onRedo: () => void;
  optimising: Optimising | null;
  outcome: OptimisationOutcome | null;
  onOptimise: () => void;
  onCancelOptimise: () => void;
}

export function Workspace(props: WorkspaceProps) {
  const { optimising, outcome, onOptimise, onCancelOptimise, phase, state, geometry, strain, models, activity, layout, onResize, onCollapse, onOpenSmiles, onSubmitSmiles, onMoveAtom, onSetBondLength, onSetBondTorsion, onSetAngle, onLockAtoms, canLock, lockPressed, onToggleLock, onDragAtoms, onDropAtoms, onCancelDrag, onMoveMolecule, onDragMolecule, onDropMolecule, onCancelMoleculeDrag, onDragBond, onDropBond, onCancelBondDrag, onUndo, onRedo } = props;

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
        optimising={optimising}
        canOptimise={phase.kind === "open" && state !== null}
        onOptimise={onOptimise}
        onCancelOptimise={onCancelOptimise}
        tools={models.tools}
        canLock={canLock}
        lockPressed={lockPressed}
        onToggleLock={onToggleLock}
        layout={layout}
        onCollapse={onCollapse}
        canUndo={state?.can_undo ?? false}
        canRedo={state?.can_redo ?? false}
        saved={state?.saved_molecule_id != null}
        strain={strain}
        onUndo={onUndo}
        onRedo={onRedo}
      />

      <SmilesBar state={state} onSubmit={onSubmitSmiles} />

      <Side panel="outliner" name="Outliner" collapsed={layout.outlinerCollapsed} onCollapse={onCollapse}>
        <Outliner state={state} models={models} />
      </Side>

      {!layout.outlinerCollapsed && (
        <div className="splitter-cell splitter-left">
          <Splitter panel="outliner" axis="vertical" size={layout.outliner} towards={1} onResize={onResize} />
        </div>
      )}

      <div className="viewport-cell">
        <Viewport
          phase={phase}
          state={state}
          geometry={geometry}
          models={models}
          onOpenSmiles={onOpenSmiles}
          onDragAtoms={onDragAtoms}
          onDropAtoms={onDropAtoms}
          onCancelDrag={onCancelDrag}
          onDragMolecule={onDragMolecule}
          onDropMolecule={onDropMolecule}
          onCancelMoleculeDrag={onCancelMoleculeDrag}
          onDragBond={onDragBond}
          onDropBond={onDropBond}
          onCancelBondDrag={onCancelBondDrag}
        />
        <OptimiseNotice outcome={outcome} />
      </div>

      {!layout.inspectorCollapsed && (
        <div className="splitter-cell splitter-right">
          <Splitter panel="inspector" axis="vertical" size={layout.inspector} towards={-1} onResize={onResize} />
        </div>
      )}

      <Side panel="inspector" name="Inspector" collapsed={layout.inspectorCollapsed} onCollapse={onCollapse}>
        <Inspector state={state} geometry={geometry} models={models} onMoveAtom={onMoveAtom} onSetBondLength={onSetBondLength} onSetBondTorsion={onSetBondTorsion} onSetAngle={onSetAngle} onMoveMolecule={onMoveMolecule} onLockAtoms={onLockAtoms} />
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
            <AnalysisPanel activity={activity} state={state} geometry={geometry} outcome={outcome} models={models} />
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
