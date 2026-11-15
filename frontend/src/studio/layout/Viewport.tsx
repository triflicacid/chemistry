import { useEffect, useRef, useState } from "react";
import type { Item } from "../selection/items";
import type { Models, PickMode } from "../selection/models";
import { useObservable } from "../selection/observable";
import type { StateIndex } from "../selection/StateIndex";
import type { SessionState } from "../session/messages";
import type { SessionPhase } from "../session/useSession";
import { type StudioPick, StudioView } from "../viewport/StudioView";
import { OpenMolecule } from "./OpenMolecule";

export interface ViewportProps {
  phase: SessionPhase;
  state: SessionState | null;
  models: Models;
  onOpenSmiles: (smiles: string) => void;
}

/** The canvas, which fills whatever space the workspace leaves it, and the molecule drawn on it. */
export function Viewport({ phase, state, models, onOpenSmiles }: ViewportProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [view, setView] = useState<StudioView | null>(null);
  const [axes, setAxes] = useState(true);
  const [mode, setMode] = useState<PickMode>("element");
  const { selection, hover, index } = models;
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
    });
  }, [view, index, mode, selection, hover]);

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
