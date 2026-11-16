import {useCallback, useEffect, useMemo, useState} from "react";
import {Workspace} from "./layout/Workspace";
import {createModels} from "./models/models";
import {TOOL_ORDER} from "./models/ToolModel";
import {useObservable} from "./models/observable";
import {StateIndex} from "./geometry/StateIndex";
import {useChemistry} from "./chemistry/useChemistry";
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
  const chemistry = useChemistry(state);
  const index = useMemo(() => (state ? new StateIndex(state, chemistry.chemistry?.rings) : null), [state, chemistry]);
  const models = useMemo(() => ({ ...shared, index, chemistry }), [shared, index, chemistry]);

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

  const { tool, rotateTarget, editClick } = useObservable(shared.tools);
  useHotkeys([
    { ...KEYS.chooseTool, run: (event) => shared.tools.use(TOOL_ORDER[Number(event.key) - 1] ?? tool) },
    {
      ...KEYS.switchChoice,
      run: (event) => {
        // Tab on a button or a field is moving the focus about, and is left to do it
        if (event.target instanceof HTMLElement && event.target !== document.body && event.target.tagName !== "CANVAS") {
          return;
        }
        if (tool === "rotate") {
          event.preventDefault();
          shared.tools.rotateTarget(rotateTarget === "molecule" ? "internal" : "molecule");
        } else if (tool === "edit") {
          event.preventDefault();
          shared.tools.clickTo(editClick === "add" ? "select" : "add");
        }
      },
    },
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
