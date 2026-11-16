import {useCallback, useEffect, useMemo, useState} from "react";
import {Workspace} from "./layout/Workspace";
import {createModels} from "./models/models";
import {useObservable} from "./models/observable";
import {StateIndex} from "./geometry/StateIndex";
import {useSession} from "./session/useSession";
import {SessionProvider} from "./session/SessionContext";
import {KEYS} from "./keys/keymap";
import {useHotkeys} from "./keys/useHotkeys";
import type {Panel} from "./layoutStore";
import {LayoutStore} from "./layoutStore";

/** How long to let a drag settle before writing the layout down. */
const SETTLE_MS = 200;

export function App() {
  const store = useMemo(() => new LayoutStore(), []);
  const [layout, setLayout] = useState(store.layout);
  const session = useSession();
  const { phase, state, geometry, activity, submitSmiles, lockAtoms, undo, redo } = session;

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

  // the toolbar button and the L key lock the selected atoms, or unlock them if all of them are locked already.
  // A selected molecule stands for every atom in it
  const { items: selected } = useObservable(shared.selection);
  const chosen = useMemo(() => (index ? [...index.members(selected).atoms] : []), [index, selected]);
  const allLocked = chosen.length > 0 && chosen.every((id) => state?.locked.includes(id));
  const toggleLock = useCallback(() => {
    if (chosen.length > 0) {
      void lockAtoms(chosen, !allLocked);
    }
  }, [chosen, allLocked, lockAtoms]);

  useHotkeys([
    { ...KEYS.clearSelection, run: () => shared.selection.clear() },
    {
      ...KEYS.undo,
      run: (event) => {
        event.preventDefault();
        void undo();
      },
    },
    {
      ...KEYS.redo,
      run: (event) => {
        event.preventDefault();
        void redo();
      },
    },
    {
      ...KEYS.redoAlternative,
      run: (event) => {
        event.preventDefault();
        void redo();
      },
    },
    { ...KEYS.toggleLock, run: toggleLock },
  ]);

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

  const resize = useCallback((panel: Panel, size: number) => {
    setLayout((before) => (before[panel] === size ? before : { ...before, [panel]: size }));
  }, []);

  const collapse = useCallback((panel: Panel, collapsed: boolean) => {
    setLayout((before) => ({ ...before, [`${panel}Collapsed`]: collapsed }));
  }, []);

  return (
    <SessionProvider value={session}>
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
        onSubmitSmiles={submit}
        canLock={chosen.length > 0}
        lockPressed={allLocked}
        onToggleLock={toggleLock}
      />
    </SessionProvider>
  );
}
