import { useCallback, useRef } from "react";
import type { Panel } from "../layoutStore";
import { clamp } from "../layoutStore";
import * as React from "react";

export interface SplitterProps {
  panel: Panel;
  /** Which way the handle slides, and so which pointer axis sizes the panel. */
  axis: "vertical" | "horizontal";
  /** The size the panel has now, in pixels. */
  size: number;
  /** Whether dragging towards the pointer makes the panel larger or smaller. */
  towards: 1 | -1;
  onResize: (panel: Panel, size: number) => void;
}

/** A drag handle between two panels, which resizes the one it belongs to. */
export function Splitter({ panel, axis, size, towards, onResize }: SplitterProps) {
  const from = useRef(0);
  const started = useRef(0);

  const move = useCallback(
    (event: PointerEvent) => {
      const along = axis === "vertical" ? event.clientX : event.clientY;
      onResize(panel, clamp(panel, started.current + towards * (along - from.current)));
    },
    [axis, onResize, panel, towards],
  );

  const grab = useCallback(
    (event: React.PointerEvent<HTMLDivElement>) => {
      from.current = axis === "vertical" ? event.clientX : event.clientY;
      started.current = size;

      const release = () => {
        window.removeEventListener("pointermove", move);
        window.removeEventListener("pointerup", release);
        document.body.classList.remove("resizing");
      };

      window.addEventListener("pointermove", move);
      window.addEventListener("pointerup", release);
      document.body.classList.add("resizing");
    },
    [axis, move, size],
  );

  const nudge = useCallback(
    (event: React.KeyboardEvent<HTMLDivElement>) => {
      const step = event.shiftKey ? 32 : 8;
      const less = axis === "vertical" ? "ArrowLeft" : "ArrowUp";
      const more = axis === "vertical" ? "ArrowRight" : "ArrowDown";

      if (event.key === less || event.key === more) {
        event.preventDefault();
        onResize(panel, clamp(panel, size + towards * (event.key === more ? step : -step)));
      }
    },
    [axis, onResize, panel, size, towards],
  );

  return (
    <div
      className={`splitter splitter-${axis}`}
      role="separator"
      aria-label={`Resize the ${panel} panel`}
      aria-orientation={axis}
      aria-valuenow={size}
      tabIndex={0}
      onPointerDown={grab}
      onKeyDown={nudge}
    />
  );
}
