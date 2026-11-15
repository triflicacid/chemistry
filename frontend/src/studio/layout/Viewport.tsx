import {useEffect, useRef, useState} from "react";
import type {Item} from "../selection/items";
import type {Models, PickMode} from "../selection/models";
import {useObservable} from "../selection/observable";
import type {StateIndex} from "../selection/StateIndex";
import type {AtomMove, GeometryResult, Pose, SessionState} from "../session/messages";
import type {SessionPhase} from "../session/useSession";
import {type Constraint, constraintForKey} from "../viewport/dragPlane";
import type {GridKind, GridPlaneChoice} from "../viewport/grid";
import {type StudioPick, StudioView} from "../viewport/StudioView";
import {isCurrent} from "./findings";
import {OpenMolecule} from "./OpenMolecule";

export interface ViewportProps {
  phase: SessionPhase;
  state: SessionState | null;
  /** What the server last said about the geometry. */
  geometry: GeometryResult | null;
  models: Models;
  onOpenSmiles: (smiles: string) => void;
  /** Atoms have been dragged to positions, and the drag is not over. */
  onDragAtoms: (moves: AtomMove[]) => void;
  /** Atoms have been dropped at positions, which ends their drag. */
  onDropAtoms: (moves: AtomMove[]) => void;
  /** A drag was abandoned, and the atoms are back at `moves`' positions. */
  onCancelDrag: (moves: AtomMove[]) => void;
  /** The molecule has been dragged to a pose, and the drag is not over. */
  onDragMolecule: (pose: Pose) => void;
  /** The molecule has been dropped at a pose, which ends its drag. */
  onDropMolecule: (pose: Pose) => void;
  /** A drag of the molecule was abandoned, and it is back at `from`. */
  onCancelMoleculeDrag: (from: Pose) => void;
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
export function Viewport({
  phase,
  state,
  geometry,
  models,
  onOpenSmiles,
  onDragAtoms,
  onDropAtoms,
  onCancelDrag,
  onDragMolecule,
  onDropMolecule,
  onCancelMoleculeDrag,
}: ViewportProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [view, setView] = useState<StudioView | null>(null);
  const [axes, setAxes] = useState(false);
  const [grid, setGrid] = useState<GridKind | "off">("off");
  const [gridPlane, setGridPlane] = useState<GridPlaneChoice>("xz");
  const [debug, setDebug] = useState(false);
  const [mode, setMode] = useState<PickMode>("element");
  const { selection, hover, tools, index } = models;
  const { tool, constraint, space, pivot, point, strain } = useObservable(tools);
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
    view?.setGrid(grid === "off" ? null : grid, gridPlane);
  }, [view, grid, gridPlane]);

  // a result says where the strain was at the version it was made from, so it is only drawn while the session is still there
  const markable = strain && isCurrent(geometry, state);
  useEffect(() => {
    view?.setStrain(markable ? geometry : null);
  }, [view, markable, geometry]);

  useEffect(() => {
    view?.setMoving(tool === "move");
    view?.setPanning(tool === "pan");
  }, [view, tool]);

  useEffect(() => {
    view?.setConstraint(constraint);
  }, [view, constraint]);

  useEffect(() => {
    view?.setSpace(space);
  }, [view, space]);

  useEffect(() => {
    view?.setPivot(pivot, point);
  }, [view, pivot, point]);

  // the whole molecule is moved or turned with handles, which are there while a molecule is selected and a tool that uses them is
  const moleculeSelected = selected.items.some((item) => item.kind === "molecule");
  const atomsSelected = selected.items.some((item) => item.kind === "atom");
  const gizmo = tool === "move"
      ? (moleculeSelected ? "translate" : atomsSelected ? "atoms" : null)
      : (tool === "rotate" && moleculeSelected ? "rotate" : null);
  useEffect(() => {
    view?.setGizmo(gizmo);
  }, [view, gizmo]);

  // a selection with a locked atom in it cannot be moved, so there is nothing for a constraint to hold
  const blocked = state !== null && index !== null && [...index.members(selected.items).atoms].some((id) => state.locked.includes(id));

  // X, Y and Z hold a move to an axis, and shift with one holds it to the plane across that axis
  useEffect(() => {
    if (tool !== "move" || blocked) {
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
  }, [tool, tools, blocked]);

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
        // with the rotate tool an atom stands for its molecule, turned about that atom
        if (pick?.kind === "atom" && tools.get().tool === "rotate") {
          const fragment = index.fragmentOf(pick);
          if (fragment !== null) {
            // clicking what is selected already would deselect it, and here it only moves the pivot
            if (!selection.get().items.some((item) => item.kind === "molecule" && item.id === fragment)) {
              selection.select({ kind: "molecule", id: fragment });
            }
            tools.pivotOn("anchor");
            view.anchorOnAtom(pick.id);
            return;
          }
        }

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
      constraint: (held) => tools.constrain(held),
      moleculeDrag: (pose, done) => (done ? onDropMolecule(pose) : onDragMolecule(pose)),
      moleculeDragCancel: onCancelMoleculeDrag,
    });
  }, [view, index, mode, selection, hover, tools, onDragAtoms, onDropAtoms, onCancelDrag, onDragMolecule, onDropMolecule, onCancelMoleculeDrag]);

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

  const screenshot = () => {
    if (!view) {
      return;
    }
    const link = document.createElement("a");
    link.href = view.capture();
    link.download = "molecule.png";
    link.click();
  };

  return (
    <section className="viewport" aria-label="3D viewport">
      <canvas ref={canvasRef} className="viewport-canvas" />
      {state && (
        <div className="viewport-tools" role="group" aria-label="View">
          {(tool === "move" || (tool === "rotate" && moleculeSelected)) && (
            <div className="tool-group" role="group" aria-label="Space">
              {(["world", "local"] as const).map((each) => (
                <button
                  key={each}
                  type="button"
                  className="tool"
                  aria-pressed={space === each}
                  title={
                    each === "world"
                      ? "Measure moves and handles along the scene's axes"
                      : "Measure moves and handles along the molecule's own axes, which turn with it"
                  }
                  onClick={() => tools.useSpace(each)}
                >
                  {each === "world" ? "World" : "Local"}
                </button>
              ))}
            </div>
          )}
          {tool === "rotate" && !moleculeSelected && <span className="viewport-hint">Select a molecule, or click an atom, to turn it</span>}
          {tool === "move" && !moleculeSelected && (
            <div className="tool-group" role="group" aria-label="Move constraint">
              {CONSTRAINTS.map(({ value, label, title }) => (
                <button
                  key={value}
                  type="button"
                  className="tool"
                  aria-pressed={constraint === value}
                  disabled={blocked}
                  title={blocked ? "A locked atom is selected, so the selection cannot be moved" : title}
                  onClick={() => tools.constrain(value)}
                >
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
          <label className="tool-select" title="Show a grid: lines every angstrom, or rings and spokes about the orbit anchor. In Local space a square grid turns with the molecule">
            Grid
            <select value={grid} onChange={(event) => setGrid(event.target.value as GridKind | "off")}>
              <option value="off">Off</option>
              <option value="cartesian">Square</option>
              <option value="polar">Polar</option>
            </select>
          </label>
          {grid !== "off" && (
            <label className="tool-select" title="The plane the grid lies in, named by the scene axes that span it, or all three at once (which reach less far)">
              Plane
              <select value={gridPlane} onChange={(event) => setGridPlane(event.target.value as GridPlaneChoice)}>
                <option value="xz">XZ</option>
                <option value="xy">XY</option>
                <option value="yz">YZ</option>
                <option value="all">All</option>
              </select>
            </label>
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
          <button type="button" className="tool" title="Save a picture of what the camera sees as a PNG" onClick={screenshot}>
            Screenshot
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
