import { useCallback, useEffect, useMemo, useState } from "react";
import { Workspace } from "./layout/Workspace";
import { createModels } from "./selection/models";
import { StateIndex } from "./selection/StateIndex";
import { useSession } from "./session/useSession";
import type { Panel } from "./store";
import { StudioStore } from "./store";

/** How long to let a drag settle before writing the layout down. */
const SETTLE_MS = 200;

export function App() {
  const store = useMemo(() => new StudioStore(), []);
  const [layout, setLayout] = useState(store.layout);
  const { phase, state, activity, openSmiles } = useSession();

  const shared = useMemo(createModels, []);
  const index = useMemo(() => (state ? new StateIndex(state) : null), [state]);
  const models = useMemo(() => ({ ...shared, index }), [shared, index]);

  // identifiers change when the structure does, so what no longer exists must not stay selected or hovered
  useEffect(() => {
    if (index) {
      shared.selection.prune(index);
      shared.hover.prune(index);
    } else {
      shared.selection.clear();
      shared.hover.hover(null);
    }
  }, [index, shared]);

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
      models={models}
      activity={activity}
      layout={layout}
      onResize={resize}
      onCollapse={collapse}
      onOpenSmiles={openSmiles}
    />
  );
}
