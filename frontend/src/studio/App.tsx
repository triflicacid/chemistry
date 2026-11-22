import {useCallback, useEffect, useMemo, useState} from "react";
import {Workspace} from "./layout/Workspace";
import {createModels} from "./models/models";
import {TOOL_ORDER} from "./models/ToolModel";
import {useObservable} from "./models/observable";
import {answers, fragmentsFor, resolveTargets} from "./layout/orbitals";
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
  const { phase, state, geometry, activity, submitSmiles, lockAtoms, undo, redo, requestCharges, requestForces, requestOrbitals, requestOrbital, orbitals, requestEsp, requestDensity } = session;

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

  // charges are only worked out while something is coloured by them, and again whenever the session changes under them.
  // The server waits for the session to be left alone, so a drag asks many times and is answered once
  const { colourBy, chargeModel, forceArrows, surface } = useObservable(shared.properties);
  const version = state?.version;
  const sessionId = state?.session_id;
  useEffect(() => {
    if (colourBy === "charge" && sessionId !== undefined) {
      requestCharges(chargeModel);
    }
  }, [colourBy, chargeModel, sessionId, version, requestCharges]);

  // forces are likewise only worked out while they are shown
  useEffect(() => {
    if (forceArrows && sessionId !== undefined) {
      requestForces();
    }
  }, [forceArrows, sessionId, version, requestForces]);

  // the density and the potential are likewise only worked out while a surface of them is shown, for the whole session with the automatic kind of calculation
  useEffect(() => {
    if (surface === "esp" && sessionId !== undefined) {
      requestEsp("mulliken", null);
    }
  }, [surface, sessionId, version, requestEsp]);
  useEffect(() => {
    if (surface === "density" && sessionId !== undefined) {
      requestDensity("mulliken", null);
    }
  }, [surface, sessionId, version, requestDensity]);

  // an orbital that is shown is worked out again whenever the session changes under it, as are the levels it is one of
  const { selected: shownOrbital, kind: orbitalKind, scope: orbitalScope, count: orbitalCount } = useObservable(shared.orbital);
  // the molecules are named by an atom each, which is a new list on every render, so the list is kept as one string
  const orbitalFragments = fragmentsFor(orbitalScope, state);
  const orbitalFragmentsKey = orbitalFragments.join(",");
  useEffect(() => {
    if (shownOrbital !== null && sessionId !== undefined) {
      requestOrbitals(orbitalKind, orbitalFragmentsKey === "" ? [] : orbitalFragmentsKey.split(","));
    }
  }, [shownOrbital, orbitalKind, orbitalFragmentsKey, sessionId, version, requestOrbitals]);

  // which orbitals to evaluate depends on the levels, so they are asked for once the levels for the structure as it is now arrive
  const levelsCurrent = answers(orbitals, orbitalKind, orbitalFragments) && orbitals.status === "complete" && orbitals.version === version;
  useEffect(() => {
    if (shownOrbital !== null && levelsCurrent && orbitals !== null) {
      requestOrbital(orbitalKind, resolveTargets(orbitals, shownOrbital, orbitalCount));
    }
  }, [shownOrbital, orbitalKind, orbitalCount, levelsCurrent, orbitals, requestOrbital]);

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
          shared.tools.clickTo(editClick === "add" ? "ring" : editClick === "ring" ? "select" : "add");
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
