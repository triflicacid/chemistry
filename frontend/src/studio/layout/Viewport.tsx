import { useEffect, useRef } from "react";
import type { SessionPhase } from "../session/useSession";
import { OpenMolecule } from "./OpenMolecule";

export interface ViewportProps {
  phase: SessionPhase;
  onOpenSmiles: (smiles: string) => void;
  onCanvas?: (canvas: HTMLCanvasElement | null) => void;
}

/** The canvas, which fills whatever space the workspace leaves it. */
export function Viewport({ phase, onOpenSmiles, onCanvas }: ViewportProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) {
      return;
    }

    const resize = () => {
      const scale = window.devicePixelRatio || 1;
      const width = Math.max(1, Math.round(canvas.clientWidth * scale));
      const height = Math.max(1, Math.round(canvas.clientHeight * scale));

      // assigning either of these clears the canvas, so only do it when the size really changed
      if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width;
        canvas.height = height;
      }
    };

    resize();
    const watcher = new ResizeObserver(resize);
    watcher.observe(canvas);
    onCanvas?.(canvas);

    return () => {
      watcher.disconnect();
      onCanvas?.(null);
    };
  }, [onCanvas]);

  return (
    <section className="viewport" aria-label="3D viewport">
      <canvas ref={canvasRef} className="viewport-canvas" />
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
