import { useCallback, useEffect, useMemo, useState } from "react";
import { Workspace } from "./layout/Workspace";
import { useSession } from "./session/useSession";
import type { Panel } from "./store";
import { StudioStore } from "./store";

/** How long to let a drag settle before writing the layout down. */
const SETTLE_MS = 200;

export function App() {
  const store = useMemo(() => new StudioStore(), []);
  const [layout, setLayout] = useState(store.layout);
  const { phase, state, activity, openSmiles } = useSession();

  // a drag changes the size at pointer rate, so the write waits for it to stop
  useEffect(() => {
    const timer = setTimeout(() => store.rememberLayout(layout), SETTLE_MS);
    return () => clearTimeout(timer);
  }, [layout, store]);

  const resize = useCallback((panel: Panel, size: number) => {
    setLayout((before) => (before[panel] === size ? before : { ...before, [panel]: size }));
  }, []);

  const collapse = useCallback((panel: Panel, collapsed: boolean) => {
    setLayout((before) => ({ ...before, [`${panel}Collapsed`]: collapsed }));
  }, []);

  return (
    <Workspace
      phase={phase}
      state={state}
      activity={activity}
      layout={layout}
      onResize={resize}
      onCollapse={collapse}
      onOpenSmiles={openSmiles}
    />
  );
}
