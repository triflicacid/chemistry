import {useCallback, useEffect, useMemo, useState} from "react";
import type {Vec3} from "../api/types/structure";
import type {AtomMove, Pose} from "./session/messages";
import {Workspace} from "./layout/Workspace";
import {createModels} from "./selection/models";
import {useObservable} from "./selection/observable";
import {StateIndex} from "./selection/StateIndex";
import {useSession} from "./session/useSession";
import type {Panel} from "./store";
import {StudioStore} from "./store";

/** How long to let a drag settle before writing the layout down. */
const SETTLE_MS = 200;

export function App() {
  const store = useMemo(() => new StudioStore(), []);
  const [layout, setLayout] = useState(store.layout);
  const {
    phase,
    state,
    geometry,
    activity,
    openSmiles,
    submitSmiles,
    moveAtom,
    dragAtoms,
    dropAtoms,
    cancelDrag,
    moveMolecule,
    dragMolecule,
    dropMolecule,
    cancelMoleculeDrag,
    lockAtoms,
    optimising,
    outcome,
    optimise,
    cancelOptimise,
    undo,
    redo,
  } = useSession();

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
  const drop = useCallback((moves: AtomMove[]) => void dropAtoms(moves), [dropAtoms]);
  const cancel = useCallback((moves: AtomMove[]) => void cancelDrag(moves), [cancelDrag]);
  const lock = useCallback((ids: string[], locked: boolean) => void lockAtoms(ids, locked), [lockAtoms]);
  const moveWhole = useCallback((pose: Pose) => void moveMolecule(pose), [moveMolecule]);
  const dropWhole = useCallback((pose: Pose) => void dropMolecule(pose), [dropMolecule]);
  const cancelWhole = useCallback((from: Pose) => void cancelMoleculeDrag(from), [cancelMoleculeDrag]);

  // the toolbar button and the L key lock the selected atoms, or unlock them if all of them are locked already.
  // A selected molecule stands for every atom in it
  const { items: selected } = useObservable(shared.selection);
  const chosen = useMemo(() => (index ? [...index.members(selected).atoms] : []), [index, selected]);
  const allLocked = chosen.length > 0 && chosen.every((id) => state?.locked.includes(id));
  const toggleLock = useCallback(() => {
    if (chosen.length > 0) {
      lock(chosen, !allLocked);
    }
  }, [chosen, allLocked, lock]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      const typing = target?.matches("input, textarea, select, [contenteditable]") ?? false;
      if (event.key.toLowerCase() === "l" && !typing && !event.ctrlKey && !event.metaKey && !event.altKey) {
        toggleLock();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [toggleLock]);

  // a result describes the version it was made at, so it is out of date once the session has moved on from that
  const strain = useMemo(() => {
    if (!geometry) {
      return null;
    }
    if (geometry.status === "failed" || geometry.strain === null) {
      return "strain unavailable";
    }
    return `strain ${geometry.strain.toFixed(1)}${geometry.version === state?.version ? "" : "…"}`;
  }, [geometry, state?.version]);

  const startOptimise = useCallback(() => void optimise(), [optimise]);
  const stopOptimise = useCallback(() => void cancelOptimise(), [cancelOptimise]);
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
      geometry={geometry}
      strain={strain}
      models={models}
      activity={activity}
      layout={layout}
      onResize={resize}
      onCollapse={collapse}
      onOpenSmiles={openSmiles}
      onSubmitSmiles={submit}
      onMoveAtom={move}
      onLockAtoms={lock}
      canLock={chosen.length > 0}
      lockPressed={allLocked}
      onToggleLock={toggleLock}
      onDragAtoms={dragAtoms}
      onDropAtoms={drop}
      onCancelDrag={cancel}
      onMoveMolecule={moveWhole}
      onDragMolecule={dragMolecule}
      onDropMolecule={dropWhole}
      onCancelMoleculeDrag={cancelWhole}
      onUndo={undoEdit}
      onRedo={redoEdit}
      optimising={optimising}
      outcome={outcome}
      onOptimise={startOptimise}
      onCancelOptimise={stopOptimise}
    />
  );
}
