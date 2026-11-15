import { useEffect, useRef, useState } from "react";
import type { SessionState } from "../session/messages";
import type { SessionPhase } from "../session/useSession";
import { StudioView } from "../viewport/StudioView";
import { OpenMolecule } from "./OpenMolecule";

export interface ViewportProps {
  phase: SessionPhase;
  state: SessionState | null;
  onOpenSmiles: (smiles: string) => void;
}

/** The canvas, which fills whatever space the workspace leaves it, and the molecule drawn on it. */
export function Viewport({ phase, state, onOpenSmiles }: ViewportProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [view, setView] = useState<StudioView | null>(null);
  const [axes, setAxes] = useState(true);

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

  return (
    <section className="viewport" aria-label="3D viewport">
      <canvas ref={canvasRef} className="viewport-canvas" />
      {state && (
        <div className="viewport-tools" role="group" aria-label="View">
          <button type="button" className="tool" title="Frame the whole molecule" onClick={() => view?.frameScene()}>
            Frame
          </button>
          <button type="button" className="tool" aria-pressed={axes} title="Show the axes" onClick={() => setAxes(!axes)}>
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
