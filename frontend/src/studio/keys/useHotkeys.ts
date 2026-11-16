import { useEffect, useRef } from "react";
import { type Binding, dispatchKey } from "./binding";

/**
 * Listens for shortcuts on the window for as long as the component is mounted.
 *
 * @param bindings the shortcuts; one that is not wanted at the moment is left out
 */
export function useHotkeys(bindings: readonly Binding[]): void {
  const latest = useRef(bindings);
  latest.current = bindings;

  useEffect(() => {
    const onCapture = (event: KeyboardEvent) => dispatchKey(latest.current, event, true);
    const onBubble = (event: KeyboardEvent) => dispatchKey(latest.current, event, false);
    window.addEventListener("keydown", onCapture, { capture: true });
    window.addEventListener("keydown", onBubble);
    return () => {
      window.removeEventListener("keydown", onCapture, { capture: true });
      window.removeEventListener("keydown", onBubble);
    };
  }, []);
}
