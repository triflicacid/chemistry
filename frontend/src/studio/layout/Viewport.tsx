import {useEffect, useRef, useState} from "react";
import type {Item} from "../selection/items";
import type {Models, PickMode} from "../selection/models";
import type {RotateMode, Space} from "../selection/ToolModel";
import {useObservable} from "../selection/observable";
import type {StateIndex} from "../selection/StateIndex";
import type {AtomMove, BondEdit, GeometryResult, Pose, SessionState} from "../session/messages";
import {movers} from "../selection/bondLength";
import {defaultReference} from "../selection/torsion";
import {angleMovers, defaultPair} from "../selection/angle";
import type {SessionPhase} from "../session/useSession";
import {type Constraint, constraintForKey} from "../viewport/dragPlane";
import type {GridKind, GridPlaneChoice} from "../viewport/grid";
import {type StudioPick, StudioView} from "../viewport/StudioView";
import {isCurrent} from "./findings";
import {OpenMolecule} from "./OpenMolecule";
import {type Segment, Segmented} from "./Segmented";

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
  /** A bond's handle has been dragged to a length or torsion, and the drag is not over. */
  onDragBond: (edit: BondEdit) => void;
  /** A bond's handle has been dropped at a length or torsion, which ends its drag. */
  onDropBond: (edit: BondEdit) => void;
  /** A drag of a bond's handle was abandoned. */
  onCancelBondDrag: () => void;
}

const GRIDS: Segment<GridKind | "off">[] = [
  { value: "off", label: "Off", title: "Show no grid" },
  { value: "cartesian", label: "Square", title: "Lines every angstrom, with a stronger one every five. In Local space it turns with the molecule" },
  { value: "polar", label: "Polar", title: "Spheres every angstrom about the orbit anchor, with lines of latitude and longitude every 30 degrees. It fills space, so it has no plane" },
];

const GRID_PLANES: Segment<GridPlaneChoice>[] = [
  { value: "xz", label: "XZ", title: "The plane spanned by the x and z axes" },
  { value: "xy", label: "XY", title: "The plane spanned by the x and y axes" },
  { value: "yz", label: "YZ", title: "The plane spanned by the y and z axes" },
  { value: "all", label: "All", title: "All three planes at once, which reach less far" },
];

const ROTATES: Segment<RotateMode>[] = [
  { value: "molecule", label: "Molecule", title: "Click an atom to turn the whole molecule about it, with rings that turn it about the pivot" },
  { value: "parts", label: "Bonds & angles", title: "Click a bond to turn one side of it about the bond (its torsion), or an atom to change an angle between two of its bonds" },
];

const SPACES: Segment<Space>[] = [
  { value: "world", label: "World", title: "Measure moves and handles along the scene's axes" },
  { value: "local", label: "Local", title: "Measure moves and handles along the molecule's own axes, which turn with it" },
];

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
  onDragBond,
  onDropBond,
  onCancelBondDrag,
}: ViewportProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [view, setView] = useState<StudioView | null>(null);
  const [axes, setAxes] = useState(false);
  const [grid, setGrid] = useState<GridKind | "off">("off");
  const [gridPlane, setGridPlane] = useState<GridPlaneChoice>("xz");
  const [debug, setDebug] = useState(false);
  const [mode, setMode] = useState<PickMode>("element");
  const { selection, hover, tools, index } = models;
  const { tool, constraint, space, rotate, pivot, point, strain, carryHydrogens, torsion, angle, hold } = useObservable(tools);
  // turning something inside a molecule is done on single atoms and bonds, whatever else is picked
  const parts = tool === "rotate" && rotate === "parts";
  const picking: PickMode = parts ? "element" : mode;
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
    view?.setCarryHydrogens(carryHydrogens);
  }, [view, carryHydrogens]);

  useEffect(() => {
    view?.setTorsion(torsion);
  }, [view, torsion]);

  useEffect(() => {
    view?.setAngle(angle);
  }, [view, angle]);

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
      : (tool === "rotate" && rotate === "molecule" && moleculeSelected ? "rotate" : null);
  useEffect(() => {
    view?.setGizmo(gizmo);
  }, [view, gizmo]);

  // turning the whole molecule shows it by its handles, so only what is hovered is tinted, and nothing is labelled
  const plain = tool === "rotate" && rotate === "molecule";
  useEffect(() => {
    view?.setPlain(plain);
  }, [view, plain]);

  // a selected bond has a handle: an arrow along it with the move tool, which slides to change its length, and a ring about
  // it with the rotate tool, which turns to change its torsion. There is none where an atom it would move is locked
  const bondItem = selected.items.length === 1 ? selected.items[0] : undefined;
  const bondId = bondItem?.kind === "bond" ? bondItem.id : null;
  const atomId = bondItem?.kind === "atom" ? bondItem.id : null;
  const locked = state?.locked;
  useEffect(() => {
    // a selected atom has a ring about it with the rotate tool, which turns to change the angle between two of its bonds
    if (view && index && atomId !== null && parts) {
      const pair = angle?.centre === atomId ? angle : defaultPair(index, atomId);
      const moving = pair && angleMovers(index, atomId, pair, hold);
      view.setBondHandle(moving && ![...moving].some((id) => locked?.includes(id)) && pair ? { mode: "angle", centre: atomId, hold, from: pair.from, to: pair.to } : null);
      return;
    }

    const bond = bondId !== null && index ? index.bonds.get(bondId) : undefined;
    if (!view || !index || !bond || (tool !== "move" && !parts)) {
      view?.setBondHandle(null);
      return;
    }

    const turning = parts;
    const moving = movers(index, bond, hold, turning);
    if (!moving || [...moving].some((id) => locked?.includes(id))) {
      view.setBondHandle(null);
      return;
    }
    if (!turning) {
      view.setBondHandle({ mode: "length", bond: bond.id, hold });
      return;
    }

    // the Inspector says which atoms it measures from when it is showing this bond, and the heaviest neighbours are the default
    const measured = torsion?.bond === bond.id ? torsion : defaultReference(index, bond);
    view.setBondHandle(measured ? { mode: "turn", bond: bond.id, hold, from: measured.first, to: measured.second } : null);
  }, [view, index, bondId, atomId, tool, parts, hold, torsion, angle, locked]);

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

  // H turns carrying hydrogens on and off. Locks do not stop it, since it is the move that a lock refuses
  useEffect(() => {
    if (tool !== "move") {
      return;
    }
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (target?.matches("input, textarea, select, [contenteditable]") || event.ctrlKey || event.metaKey || event.altKey || event.shiftKey) {
        return;
      }
      if (event.key.toLowerCase() === "h") {
        tools.carry(!tools.get().carryHydrogens);
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
      hover: (pick) => hover.hover(pick ? resolve(pick, picking, index) : null),
      click: (pick, additive) => {
        // turning the whole molecule, an atom stands for its molecule, turned about that atom
        const { tool: current, rotate: turning } = tools.get();
        if (pick?.kind === "atom" && current === "rotate" && turning === "molecule") {
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
          selection.select(resolve(pick, picking, index), additive);
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
      bondDrag: (edit, done) => (done ? onDropBond(edit) : onDragBond(edit)),
      bondDragCancel: onCancelBondDrag,
    });
  }, [view, index, picking, selection, hover, tools, onDragAtoms, onDropAtoms, onCancelDrag, onDragMolecule, onDropMolecule, onCancelMoleculeDrag, onDragBond, onDropBond, onCancelBondDrag]);

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
        <>
          <div className="viewport-tools viewport-tools-left" role="group" aria-label="Display">
            <button type="button" className="tool" aria-pressed={axes} title="Show or hide the x, y and z axes at the orbit anchor. The small triad in the corner is always shown" onClick={() => setAxes(!axes)}>
              Axes
            </button>
            <span className="segmented-captioned">
              <span className="segmented-caption">Grid</span>
              <Segmented label="Grid" value={grid} options={GRIDS} onChange={setGrid} />
            </span>
            {grid === "cartesian" && (
              <span className="segmented-captioned">
                <span className="segmented-caption">Plane</span>
                <Segmented label="Grid plane" value={gridPlane} options={GRID_PLANES} onChange={setGridPlane} />
              </span>
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
            <button
              type="button"
              className="tool"
              aria-pressed={debug}
              title="Draw guides for moving: the axis or plane the selected atom is held to, as arrows or a faint plane"
              onClick={() => setDebug(!debug)}
            >
              Debug
            </button>
            <button type="button" className="tool" title="Save a picture of what the camera sees as a PNG" onClick={screenshot}>
              Screenshot
            </button>
          </div>

          <div className="viewport-tools viewport-tools-right" role="group" aria-label="Actions">
            {tool === "rotate" && <Segmented label="What to rotate" value={rotate} options={ROTATES} onChange={(next) => tools.rotating(next)} />}
            {(tool === "move" || (tool === "rotate" && rotate === "molecule" && moleculeSelected)) && (
              <Segmented
                label="Space"
                value={space}
                options={SPACES}
                onChange={(next) => tools.useSpace(next)}
              />
            )}
            {tool === "move" && (
              <button
                type="button"
                className="tool"
                aria-pressed={carryHydrogens}
                title="When on, moving an atom carries the hydrogens bonded to it by the same amount, so their bonds keep their lengths and angles (H). Nothing is locked"
                onClick={() => tools.carry(!carryHydrogens)}
              >
                Carry H
              </button>
            )}
            {tool === "rotate" && rotate === "molecule" && !moleculeSelected && <span className="viewport-hint">Select a molecule, or click an atom, to turn it</span>}
            {parts && bondId === null && atomId === null && <span className="viewport-hint">Click a bond to turn about it, or an atom to change an angle</span>}
            {tool === "move" && !moleculeSelected && (
              <Segmented
                label="Move constraint"
                value={constraint}
                options={CONSTRAINTS.map((each) => ({
                  ...each,
                  disabled: blocked,
                  title: blocked ? "A locked atom is selected, so the selection cannot be moved" : each.title,
                }))}
                onChange={(next) => tools.constrain(next)}
              />
            )}
            <button type="button" className="tool" title="Centre the camera on the selection, or on everything if nothing is selected" onClick={frame}>
              Frame selection
            </button>
            <button
              type="button"
              className="tool"
              aria-pressed={picking === "molecule"}
              disabled={parts}
              title={parts ? "Turning a bond or an angle picks single atoms and bonds" : "When on, clicking or hovering an atom or bond picks the whole molecule it belongs to"}
              onClick={() => setMode(mode === "molecule" ? "element" : "molecule")}
            >
              Pick whole molecule
            </button>
          </div>
        </>
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
