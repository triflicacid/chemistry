import { useCallback, useEffect, useMemo, useState } from "react";
import type { Vec3 } from "../api/types/structure";
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
  const { phase, state, activity, openSmiles, submitSmiles, moveAtom, dragAtom, dropAtom, cancelDrag, undo, redo } = useSession();

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

  // Escape clears the selection, unless it is being used to leave a text field
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      const typing = target?.matches("input, textarea, select, [contenteditable]") ?? false;
      if (event.key === "Escape" && !typing) {
        shared.selection.clear();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [shared]);

  // inside a text field the keys belong to the field, which has an undo of its own
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (target?.matches("input, textarea, select, [contenteditable]") || !(event.ctrlKey || event.metaKey)) {
        return;
      }

      const key = event.key.toLowerCase();
      if (key === "z" && !event.shiftKey) {
        event.preventDefault();
        void undo();
      } else if ((key === "z" && event.shiftKey) || key === "y") {
        event.preventDefault();
        void redo();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [undo, redo]);

  // a rebuilt structure issues every identifier afresh, so none of the old ones still names what it did
  const submit = useCallback(
    async (smiles: string) => {
      const result = await submitSmiles(smiles);
      if (result.kind !== "refused") {
        shared.selection.clear();
        shared.hover.hover(null);
      }
      return result;
    },
    [shared, submitSmiles],
  );

  // stable, because the viewport listens afresh whenever one of these changes, and the session redraws this at every patch of a drag
  const move = useCallback((id: string, position: Vec3) => void moveAtom(id, position), [moveAtom]);
  const drop = useCallback((id: string, position: Vec3) => void dropAtom(id, position), [dropAtom]);
  const cancel = useCallback((id: string, from: Vec3) => void cancelDrag(id, from), [cancelDrag]);
  const undoEdit = useCallback(() => void undo(), [undo]);
  const redoEdit = useCallback(() => void redo(), [redo]);

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
      onSubmitSmiles={submit}
      onMoveAtom={move}
      onDragAtom={dragAtom}
      onDropAtom={drop}
      onCancelDrag={cancel}
      onUndo={undoEdit}
      onRedo={redoEdit}
    />
  );
}
