import { useEffect, useRef, useState } from "react";
import type { Item } from "../selection/items";
import type { Models, PickMode } from "../selection/models";
import { useObservable } from "../selection/observable";
import type { StateIndex } from "../selection/StateIndex";
import type { AtomMove, SessionState } from "../session/messages";
import type { SessionPhase } from "../session/useSession";
import { type Constraint, constraintForKey } from "../viewport/dragPlane";
import { type StudioPick, StudioView } from "../viewport/StudioView";
import { OpenMolecule } from "./OpenMolecule";

export interface ViewportProps {
  phase: SessionPhase;
  state: SessionState | null;
  models: Models;
  onOpenSmiles: (smiles: string) => void;
  /** Atoms have been dragged to positions, and the drag is not over. */
  onDragAtoms: (moves: AtomMove[]) => void;
  /** Atoms have been dropped at positions, which ends their drag. */
  onDropAtoms: (moves: AtomMove[]) => void;
  /** A drag was abandoned, and the atoms are back at `moves`' positions. */
  onCancelDrag: (moves: AtomMove[]) => void;
}

const CONSTRAINTS: { value: Constraint; label: string; title: string }[] = [
  { value: "free", label: "Free", title: "Move in the plane facing the camera" },
  { value: "x", label: "X", title: "Move along the x axis (X)" },
  { value: "y", label: "Y", title: "Move along the y axis (Y)" },
  { value: "z", label: "Z", title: "Move along the z axis (Z)" },
  { value: "yz", label: "YZ", title: "Move in the yz plane (Shift+X)" },
  { value: "xz", label: "XZ", title: "Move in the xz plane (Shift+Y)" },
  { value: "xy", label: "XY", title: "Move in the xy plane (Shift+Z)" },
];

/** The canvas, which fills whatever space the workspace leaves it, and the molecule drawn on it. */
export function Viewport({ phase, state, models, onOpenSmiles, onDragAtoms, onDropAtoms, onCancelDrag }: ViewportProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [view, setView] = useState<StudioView | null>(null);
  const [axes, setAxes] = useState(false);
  const [debug, setDebug] = useState(false);
  const [mode, setMode] = useState<PickMode>("element");
  const { selection, hover, tools, index } = models;
  const { tool, constraint } = useObservable(tools);
  const selected = useObservable(selection);
  const hovered = useObservable(hover);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) {
      return;
    }
    const made = new StudioView(canvas);
    setView(made);
    return () => {
      made.dispose();
      setView(null);
    };
  }, []);

  useEffect(() => {
    if (view && state) {
      view.setState(state);
    }
  }, [view, state]);

  useEffect(() => {
    view?.setAxes(axes);
  }, [view, axes]);

  useEffect(() => {
    view?.setDebug(debug);
  }, [view, debug]);

  useEffect(() => {
    view?.setMoving(tool === "move");
  }, [view, tool]);

  useEffect(() => {
    view?.setConstraint(constraint);
  }, [view, constraint]);

  // X, Y and Z hold a move to an axis, and shift with one holds it to the plane across that axis
  useEffect(() => {
    if (tool !== "move") {
      return;
    }
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (target?.matches("input, textarea, select, [contenteditable]") || event.ctrlKey || event.metaKey || event.altKey) {
        return;
      }
      const asked = constraintForKey(tools.get().constraint, event.key, event.shiftKey);
      if (asked) {
        tools.constrain(asked);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [tool, tools]);

  useEffect(() => {
    if (!view || !index) {
      return;
    }
    view.setEmphasis({
      selected: index.members(selected.items),
      active: index.members(selected.active ? [selected.active] : []),
      hover: index.members(hovered ? [hovered] : []),
    });
  }, [view, index, selected, hovered]);

  useEffect(() => {
    if (!view || !index) {
      return;
    }
    return view.listen({
      hover: (pick) => hover.hover(pick ? resolve(pick, mode, index) : null),
      click: (pick, additive) => {
        if (pick) {
          selection.select(resolve(pick, mode, index), additive);
        } else if (!additive) {
          selection.clear();
        }
      },
      // picking an atom up selects it, so the Inspector shows where it is while it moves
      dragStart: (id) => {
        const item: Item = { kind: "atom", id };
        if (!selection.get().items.some((each) => each.kind === "atom" && each.id === id)) {
          selection.select(item);
        }
      },
      drag: (moves, done) => (done ? onDropAtoms(moves) : onDragAtoms(moves)),
      dragCancel: onCancelDrag,
    });
  }, [view, index, mode, selection, hover, onDragAtoms, onDropAtoms, onCancelDrag]);

  const frame = () => {
    if (!view || !index) {
      return;
    }
    const { atoms, bonds } = index.members(selected.items);
    const ends = [...bonds].flatMap((id) => {
      const bond = index.bonds.get(id);
      return bond ? [bond.first, bond.second] : [];
    });
    view.frameAtoms([...atoms, ...ends]);
  };

  return (
    <section className="viewport" aria-label="3D viewport">
      <canvas ref={canvasRef} className="viewport-canvas" />
      {state && (
        <div className="viewport-tools" role="group" aria-label="View">
          {tool === "move" && (
            <div className="tool-group" role="group" aria-label="Move constraint">
              {CONSTRAINTS.map(({ value, label, title }) => (
                <button key={value} type="button" className="tool" aria-pressed={constraint === value} title={title} onClick={() => tools.constrain(value)}>
                  {label}
                </button>
              ))}
            </div>
          )}
          <button type="button" className="tool" title="Centre the camera on the selection, or on everything if nothing is selected" onClick={frame}>
            Frame selection
          </button>
          <button
            type="button"
            className="tool"
            aria-pressed={mode === "molecule"}
            title="When on, clicking or hovering an atom or bond picks the whole molecule it belongs to"
            onClick={() => setMode(mode === "molecule" ? "element" : "molecule")}
          >
            Pick whole molecule
          </button>
          <button type="button" className="tool" aria-pressed={axes} title="Show or hide the x, y and z axes at the origin" onClick={() => setAxes(!axes)}>
            Axes
          </button>
          <button
            type="button"
            className="tool"
            aria-pressed={debug}
            title="Draw guides for moving: the axis or plane the selected atom is held to, as arrows or a faint plane"
            onClick={() => setDebug(!debug)}
          >
            Debug
          </button>
        </div>
      )}
      {phase.kind === "wanted" ? (
        <div className="viewport-overlay">
          <OpenMolecule message={phase.message} onOpen={onOpenSmiles} />
        </div>
      ) : (
        phase.kind !== "open" && (
          <div className="viewport-overlay">
            <p className={phase.kind === "failed" ? "notice failed" : "notice"} role="status">
              {notice(phase)}
            </p>
          </div>
        )
      )}
    </section>
  );
}

/** What a click or hover on `pick` selects: the atom or bond, or the molecule it is in. */
function resolve(pick: StudioPick, mode: PickMode, index: StateIndex): Item {
  const fragment = mode === "molecule" ? index.fragmentOf(pick) : null;
  return fragment !== null ? { kind: "molecule", id: fragment } : pick;
}

function notice(phase: SessionPhase): string {
  switch (phase.kind) {
    case "connecting":
      return "Connecting to the server…";
    case "opening":
      return "Building the structure…";
    case "failed":
      return phase.message;
    default:
      return "";
  }
}
