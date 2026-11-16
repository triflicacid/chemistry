import {useEffect, useRef, useState} from "react";
import type {SystemView} from "../geometry/conjugation";
import type {Item, PartItem} from "../models/items";
import type {Models, PickScope} from "../models/models";
import {useObservable} from "../models/observable";
import type {StateIndex} from "../geometry/StateIndex";
import {heldSide} from "../geometry/bondLength";
import {defaultPair, heldArm} from "../geometry/angle";
import type {GeometryResult, SessionState} from "../session/messages";
import type {SessionPhase} from "../session/types";
import {useSessionContext} from "../session/SessionContext";
import type {GridKind, GridPlaneChoice} from "../viewport/grid";
import {StudioView} from "../viewport/StudioView";
import {isCurrent} from "./findings";
import {OpenMolecule} from "../controls/OpenMolecule";
import {ActionTools, DisplayTools} from "./ViewportOverlay";

export interface ViewportProps {
  phase: SessionPhase;
  state: SessionState | null;
  /** What the server last said about the geometry. */
  geometry: GeometryResult | null;
  models: Models;
}

/** The canvas, which fills whatever space the workspace leaves it, and the molecule drawn on it. */
export function Viewport({ phase, state, geometry, models }: ViewportProps) {
  const {
    openSmiles,
    saveCamera,
    dragAtoms,
    dropAtoms,
    cancelDrag,
    dragMolecule,
    dropMolecule,
    cancelMoleculeDrag,
    dragBond,
    dropBond,
    cancelBondDrag,
  } = useSessionContext();
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [view, setView] = useState<StudioView | null>(null);
  const [axes, setAxes] = useState(false);
  const [grid, setGrid] = useState<GridKind | "off">("off");
  const [gridPlane, setGridPlane] = useState<GridPlaneChoice>("xz");
  const [debug, setDebug] = useState(false);
  const [look, setLook] = useState(false);
  const [mode, setMode] = useState<PickScope>("item");
  const { selection, hover, tools, index } = models;
  const toolState = useObservable(tools);
  const { tool, constraint, space, rotateTarget, pivot, point, strain, carryHydrogens, snap, torsion, angle, system, hold, axis } = toolState;
  // turning something inside a molecule is done on single atoms and bonds, whatever else is picked
  const internal = tool === "rotate" && rotateTarget === "internal";
  const picking: PickScope = internal ? "item" : mode;
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
    view?.setLookAround(look);
  }, [view, look]);

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
    if (tool === "pan") {
      hover.hover(null);
    }
  }, [tool, hover]);

  // the Inspector shows the camera, and its edits are made here
  const { camera } = models;
  useEffect(() => {
    if (!view) {
      return;
    }
    camera.report(view.cameraState());
    const stopListening = view.listen({
      camera: (now, byUser) => {
        camera.report(now);
        if (byUser) {
          saveCamera({ position: now.position, target: now.target, fov: now.fov, roll: now.roll });
        }
      },
    });
    const stopHandling = camera.handle((edit) => view.setCamera(edit));
    return () => {
      stopListening();
      stopHandling();
    };
  }, [view, camera, saveCamera]);

  // the move tool only tints the atoms, and the rotate tool shows no torsion; the select tool marks them all
  useEffect(() => {
    view?.setTorsion(tool === "move" || tool === "rotate" ? null : torsion);
  }, [view, torsion, tool]);

  useEffect(() => {
    view?.setAngle(tool === "move" ? null : angle);
  }, [view, angle, tool]);

  // the pi systems marked in the viewport: the label of the ring the Inspector shows, and every cloud turned on
  const { chemistry } = models.chemistry;
  useEffect(() => {
    if (!view || !index || !chemistry) {
      view?.setSystems([]);
      return;
    }
    const wanted = new Map<number, SystemView>();
    const want = (id: string, part: "cloud" | "label") => {
      const ring = chemistry.ring(id);
      if (!ring?.system) {
        return;
      }
      const known = wanted.get(ring.system.index);
      if (known) {
        known[part] = true;
        return;
      }
      const bonds = ring.systemBonds.flatMap((bondId) => {
        const bond = index.bonds.get(bondId);
        return bond ? [[bond.first, bond.second] as const] : [];
      });
      wanted.set(ring.system.index, {
        atoms: ring.systemAtoms,
        bonds,
        cloud: part === "cloud",
        label: part === "label",
        piElectrons: ring.system.pi_electrons,
        aromaticity: ring.system.aromaticity.toUpperCase() as SystemView["aromaticity"],
      });
    };
    system.clouds.forEach((id) => want(id, "cloud"));
    if (system.label !== null) {
      want(system.label, "label");
    }
    view.setSystems([...wanted.values()]);
  }, [view, index, chemistry, system]);

  useEffect(() => {
    view?.setSpace(space);
  }, [view, space]);

  useEffect(() => {
    view?.setPivot(pivot, point);
  }, [view, pivot, point]);

  // the axle the molecule is turned about is a bond clicked with the rotate tool, which lasts only while it is turning the molecule
  const turning = tool === "rotate" && rotateTarget === "molecule";
  useEffect(() => {
    if (!turning && axis !== null) {
      tools.turnAbout(null);
    }
    view?.setAxis(turning && axis !== null ? (index?.bonds.get(axis) ?? null) : null);
  }, [view, index, tools, turning, axis]);

  const moleculeSelected = selected.items.some((item) => item.kind === "molecule");

  // a selected bond has a handle: an arrow along it with the move tool, which slides to change its length, and a ring about
  // it with the rotate tool, which turns to change its torsion. There is none where an atom it would move is locked
  const locked = state?.locked;

  // what the viewport does with the pointer and the keyboard, and the handles it offers, follows the tool and the selection.
  // It comes after the state is shown, so that it finds the atoms it is to put handles on
  useEffect(() => {
    view?.setMode(tool, { constraint, carryHydrogens, snap, rotateTarget, selection: selected.items, index, hold, torsion, angle });
  }, [view, state, locked, tool, constraint, carryHydrogens, snap, rotateTarget, selected, index, hold, torsion, angle]);

  // a selection with a locked atom in it cannot be moved, so there is nothing for a constraint to hold
  const blocked = state !== null && index !== null && [...index.members(selected.items).atoms].some((id) => state.locked.includes(id));


  useEffect(() => {
    if (!view || !index) {
      return;
    }
    // the ends of a hovered bond are tinted as the SMILES marks them, but only while nothing is selected, as the Inspector does
    const bond = hovered?.kind === "bond" && selected.items.length === 0 ? index.bonds.get(hovered.id) : undefined;
    // turning in a molecule leaves one side where it is, which is shown so that the Hold choice can be seen
    const turned = tool === "rotate" && rotateTarget === "internal" && selected.items.length === 1 ? selected.items[0] : undefined;
    const turnedBond = turned?.kind === "bond" ? index.bonds.get(turned.id) : undefined;
    const pair = turned?.kind === "atom" ? (angle?.centre === turned.id ? angle : defaultPair(index, turned.id)) : null;
    const held = turnedBond ? heldSide(index, turnedBond, hold) : turned && pair ? heldArm(index, turned.id, pair, hold) : null;
    view.setEmphasis({
      held: held ?? undefined,
      anchor: held?.anchor,
      selected: index.members(selected.items),
      active: index.members(selected.active ? [selected.active] : []),
      hover: index.members(hovered ? [hovered] : []),
      ends: bond ? [{ id: bond.first, colour: 1 }, { id: bond.second, colour: 2 }] : [],
    });
  }, [view, index, selected, hovered, tool, rotateTarget, hold]);

  useEffect(() => {
    if (!view || !index) {
      return;
    }
    return view.listen({
      hover: (pick) => hover.hover(pick ? resolve(pick, picking, index) : null),
      click: (pick, additive) => {
        // turning the whole molecule, an atom stands for its molecule, turned about that atom, and a bond for its molecule,
        // turned about the bond as an axle
        const { tool: current, rotateTarget: turning } = tools.get();
        if ((pick?.kind === "atom" || pick?.kind === "bond") && current === "rotate" && turning === "molecule") {
          const fragment = index.fragmentOf(pick);
          if (fragment !== null) {
            // clicking what is selected already would deselect it, and here it only moves the pivot
            if (!selection.get().items.some((item) => item.kind === "molecule" && item.id === fragment)) {
              selection.select({ kind: "molecule", id: fragment });
            }
            if (pick.kind === "bond") {
              tools.turnAbout(pick.id);
            } else {
              tools.turnAbout(null);
              tools.pivotOn("anchor");
              view.anchorOnAtom(pick.id);
            }
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
      drag: (moves, done) => (done ? void dropAtoms(moves) : dragAtoms(moves)),
      dragCancel: (moves) => void cancelDrag(moves),
      constraint: (held) => tools.constrain(held),
      carryHydrogens: (on) => tools.carry(on),
      moleculeDrag: (pose, done) => (done ? void dropMolecule(pose) : dragMolecule(pose)),
      moleculeDragCancel: (from) => void cancelMoleculeDrag(from),
      bondDrag: (edit, done) => (done ? void dropBond(edit) : dragBond(edit)),
      bondDragCancel: () => void cancelBondDrag(),
    });
  }, [view, index, picking, selection, hover, tools, dragAtoms, dropAtoms, cancelDrag, dragMolecule, dropMolecule, cancelMoleculeDrag, dragBond, dropBond, cancelBondDrag]);

  useEffect(() => {
    if (!view) {
      return;
    }
    return models.focus.subscribe((atoms) => view.frameAtoms(atoms));
  }, [view, models.focus]);

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
          <DisplayTools
            axes={axes}
            onAxes={setAxes}
            grid={grid}
            onGrid={setGrid}
            gridPlane={gridPlane}
            onGridPlane={setGridPlane}
            strain={strain}
            tools={tools}
            debug={debug}
            onDebug={setDebug}
            onScreenshot={screenshot}
          />

          <ActionTools
            tools={tools}
            state={toolState}
            gridShown={grid !== "off"}
            moleculeSelected={moleculeSelected}
            blocked={blocked}
            look={look}
            onLook={setLook}
            picking={picking}
            onPickScope={() => setMode(mode === "molecule" ? "item" : "molecule")}
            onFrame={frame}
            onCentre={() => view?.centreCamera()}
            onReset={() => view?.resetCamera()}
          />
        </>
      )}
      {phase.kind === "wanted" ? (
        <div className="viewport-overlay">
          <OpenMolecule message={phase.message} onOpen={openSmiles} />
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
function resolve(pick: PartItem, mode: PickScope, index: StateIndex): Item {
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
