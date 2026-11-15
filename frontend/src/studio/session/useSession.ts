import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {ChemistryApi} from "../../api/ChemistryApi";
import {ApiError} from "../../api/http";
import {describing, describingSmiles, requested} from "./location";
import type {Vec3} from "../../api/types/structure";
import type {
  AtomMove,
  EditApplied,
  GeometryResult,
  OptimiseProgress,
  OptimiseResult,
  PatchAck,
  Pose,
  SessionState,
} from "./messages";
import {samePose} from "./pose";
import {StudioError, StudioSocket} from "./studioSocket";

/** How far the session has got towards being editable. */
export type SessionPhase =
  | { kind: "connecting" }
  | { kind: "opening" }
  | { kind: "open" }
  /** Nothing to open: the URL named none, or the molecule it named is gone. */
  | { kind: "wanted"; message: string }
  | { kind: "failed"; message: string };

/** One line of the activity log. */
export interface Activity {
  at: number;
  message: string;
  failed: boolean;
}

/** How many lines of activity are kept. */
const HISTORY = 200;

/** The least time between the patches a drag sends, so a drag sends about ten a second however fast the pointer moves. */
export const DRAG_INTERVAL_MS = 100;

/** Something being dragged, atoms or the whole molecule, and how much of the drag the session has been told. */
interface Drag {
  /** What is in the drag, so that a report about something different starts a new drag. */
  key: string;
  /** Where the pointer last put it: a list of atom moves, or a pose. */
  latest: unknown;
  /** What the last patch sent, so that one that would repeat it is not. */
  sent: unknown;
  /** Whether two reports put it in the same place. */
  same: (first: never, second: never) => boolean;
  /** Sends a report to the session as part of the drag. */
  transmit: (report: never) => void;
  /** When the last patch was sent. */
  at: number;
  timer: ReturnType<typeof setTimeout> | undefined;
  /** Set when the drag ends, so that patches still waiting their turn are dropped. */
  closed: boolean;
}

/** How a submitted SMILES came out. */
export type SubmitResult =
  | { kind: "replaced" }
  | { kind: "conflict" }
  | { kind: "refused"; message: string };

/** How an edit to the structure came out. */
export type EditResult =
  | { kind: "applied" }
  | { kind: "conflict" }
  | { kind: "refused"; message: string };

/** An optimisation the server is running, and how far it has got. */
export interface Optimising {
  /** How many steps it has taken, which is zero until the server first reports. */
  iteration: number;
}

/** How the latest optimisation ended, kept so the user can be shown it and look back at it. */
export interface OptimisationOutcome {
  /** When it ended, which tells one outcome from the next. */
  at: number;
  status: OptimiseResult["status"];
  /** The toolbar's strain before and after, or null if it was not measured. */
  strainBefore: number | null;
  strainAfter: number | null;
  /** How many atoms it moved. */
  atoms: number;
  iterations: number;
  /** Why it failed or was stopped. */
  message: string | null;
  warnings: string[];
}

export interface Session {
  phase: SessionPhase;
  state: SessionState | null;
  /** The optimisation under way, or null if there is none. */
  optimising: Optimising | null;
  /** How the latest optimisation ended, or null if there has been none in this session. */
  outcome: OptimisationOutcome | null;
  /**
   * How strained the geometry is, as the server last said. It describes the version in `version`, which is out of date
   * once the session has moved on; the server sends a fresh one shortly after every change.
   */
  geometry: GeometryResult | null;
  activity: Activity[];
  /** Stores a molecule written as SMILES and opens a session on it. */
  openSmiles: (smiles: string) => void;
  /** Rebuilds the open session's structure from SMILES text. */
  submitSmiles: (smiles: string) => Promise<SubmitResult>;
  /** Puts an atom at a position, as one edit that can be undone. */
  moveAtom: (id: string, position: Vec3) => Promise<EditResult>;
  /** Puts several atoms at positions, as one edit that can be undone. */
  moveAtoms: (moves: AtomMove[]) => Promise<EditResult>;
  /**
   * Reports where dragged atoms have got to. Sends at most one patch per {@link DRAG_INTERVAL_MS}, of the latest
   * positions, none of which is yet an edit to undo.
   */
  dragAtoms: (moves: AtomMove[]) => void;
  /** Ends a drag at positions, as one edit that can be undone however many patches the drag sent. */
  dropAtoms: (moves: AtomMove[]) => Promise<EditResult>;
  /** Abandons a drag, putting the atoms back at the positions they were picked up from. */
  cancelDrag: (moves: AtomMove[]) => Promise<EditResult>;
  /** Puts the molecule somewhere in the scene, as one edit that can be undone. */
  moveMolecule: (pose: Pose) => Promise<EditResult>;
  /** Reports where a dragged molecule has got to. */
  dragMolecule: (pose: Pose) => void;
  /** Ends a drag of the molecule at a pose, as one edit that can be undone however many patches the drag sent. */
  dropMolecule: (pose: Pose) => Promise<EditResult>;
  /** Abandons a drag of the molecule, putting it back at the pose it was picked up at. */
  cancelMoleculeDrag: (from: Pose) => Promise<EditResult>;
  /** Locks or unlocks atoms, so that they refuse or accept edits. A lock is saved with the molecule but is not an edit, so it cannot be undone. */
  lockAtoms: (ids: string[], locked: boolean) => Promise<EditResult>;
  /**
   * Relaxes the geometry on the server, holding locked atoms where they are. Resolves once the server has started,
   * not when it has finished: the result is applied as one edit that can be undone, and reported in the activity log.
   */
  optimise: () => Promise<EditResult>;
  /** Asks the server to stop the optimisation under way, which then ends without changing the structure. */
  cancelOptimise: () => Promise<void>;
  /** Takes back the latest edit. */
  undo: () => Promise<EditResult>;
  /** Makes the latest undone edit again. */
  redo: () => Promise<EditResult>;
}

/**
 * Connects to the editing channel and opens whatever the URL asks for, keeping the state the
 * server sends. A molecule ID that no longer exists falls back to the SMILES beside it, and
 * then to asking the user, because IDs do not survive a server restart.
 */
export function useSession(client?: ChemistryApi): Session {
  // a client built fresh each render would change the effect's dependencies every render,
  // which would close the socket and open another one on every keystroke
  const api = useMemo(() => client ?? new ChemistryApi(), [client]);

  const [phase, setPhase] = useState<SessionPhase>({ kind: "connecting" });
  const [state, setState] = useState<SessionState | null>(null);
  const [geometry, setGeometry] = useState<GeometryResult | null>(null);
  const [optimising, setOptimising] = useState<Optimising | null>(null);
  const [outcome, setOutcome] = useState<OptimisationOutcome | null>(null);
  const [activity, setActivity] = useState<Activity[]>([]);

  const socketRef = useRef<StudioSocket | null>(null);
  const held = useRef<SessionState | null>(null);
  const queue = useRef<Promise<unknown>>(Promise.resolve());
  const drag = useRef<Drag | null>(null);

  const note = useCallback((message: string, failed = false) => {
    setActivity((before) => [...before, { at: Date.now(), message, failed }].slice(-HISTORY));
  }, []);

  const adopt = useCallback((next: SessionState) => {
    // what was said of another session says nothing of this one
    if (held.current && held.current.session_id !== next.session_id) {
      setGeometry(null);
      setOptimising(null);
      setOutcome(null);
    }
    // the molecule saved is what a reload should open, since it holds the edits and the molecule opened does not
    if (next.saved_molecule_id && next.saved_molecule_id !== held.current?.saved_molecule_id) {
      describing(next.saved_molecule_id, next.smiles);
    }
    held.current = next;
    setState(next);
    setPhase({ kind: "open" });
  }, []);

  /** Stores a molecule and opens it, naming both in the URL so a reload still works. */
  const openSmiles = useCallback(
    async (socket: StudioSocket, smiles: string) => {
      setPhase({ kind: "opening" });
      try {
        const stored = await api.createMolecule(smiles);
        const opened = await socket.openSession(stored.id);
        describing(stored.id, smiles);
        adopt(opened);
        note(`Opened ${opened.smiles}`);
      } catch (cause) {
        const message = reason(cause);
        setPhase(refusedTheMolecule(cause) ? { kind: "wanted", message } : { kind: "failed", message });
        note(message, true);
      }
    },
    [adopt, api, note],
  );

  /** Opens a stored molecule, falling back to its SMILES if the server has forgotten it. */
  const openStored = useCallback(
    async (socket: StudioSocket, moleculeId: string, smiles?: string) => {
      setPhase({ kind: "opening" });
      try {
        const opened = await socket.openSession(moleculeId);
        adopt(opened);
        note(`Opened ${opened.smiles}`);
        return;
      } catch (cause) {
        if (!(cause instanceof StudioError) || cause.code !== "UNKNOWN_MOLECULE") {
          setPhase({ kind: "failed", message: reason(cause) });
          note(reason(cause), true);
          return;
        }
      }

      if (!smiles) {
        setPhase({
          kind: "wanted",
          message: "That molecule is no longer on the server. Enter its SMILES to carry on.",
        });
        return;
      }

      note("The stored molecule had gone; reading it from the URL instead");
      await openSmiles(socket, smiles);
    },
    [adopt, note, openSmiles],
  );

  useEffect(() => {
    let live = true;
    let greetings = 0;

    const socket: StudioSocket = new StudioSocket({
      onReady: () => {
        greetings += 1;

        // the first greeting is the one connect() resolves on; a later one means the socket
        // dropped and came back, so whatever the server still holds is authoritative
        if (greetings === 1 || !held.current) {
          return;
        }

        note("Reconnected");
        const { session_id, version } = held.current;
        socket
          .resync(session_id, version)
          .then(adopt)
          .catch((cause: unknown) => {
            setPhase({ kind: "wanted", message: "The editing session expired. Enter a SMILES to start again." });
            note(reason(cause), true);
          });
      },
      onDrop: (why) => {
        // the server finishes the optimisation and sends its result to whichever connection holds the session, which
        // may be the next one, so what is shown stops here and the result is still applied if it arrives
        setOptimising(null);
        note(`The connection dropped (${why}); reconnecting`, true);
      },
      onIncompatible: (error) => {
        if (held.current) {
          setPhase({ kind: "failed", message: error.message });
          note(error.message, true);
        }
      },
    });
    socketRef.current = socket;

    // the server says how strained the geometry is whenever it has had time to work it out; one older than what is held is stale
    const hearGeometry = socket.on<GeometryResult>("geometry.result", (result) => {
      if (held.current && held.current.session_id !== result.session_id) {
        return;
      }
      setGeometry((before) => (before && before.session_id === result.session_id && before.version > result.version ? before : result));
    });

    const hearProgress = socket.on<OptimiseProgress>("optimise.progress", (progress) => {
      if (held.current && held.current.session_id !== progress.session_id) {
        return;
      }
      // a report that arrives after the result, or on a connection that never asked, has nothing to update
      setOptimising((before) => (before ? { iteration: progress.iteration } : before));
    });

    const hearResult = socket.on<OptimiseResult>("optimise.result", (result) => {
      const base = held.current;
      if (!base || base.session_id !== result.session_id) {
        return;
      }

      setOptimising(null);
      setOutcome({
        at: Date.now(),
        status: result.status,
        strainBefore: result.strain_before,
        strainAfter: result.strain_after,
        atoms: result.atoms.length,
        iterations: result.iterations,
        message: result.message,
        warnings: result.warnings,
      });
      for (const warning of result.warnings) {
        note(`Optimiser: ${warning}`, true);
      }

      switch (result.status) {
        case "complete": {
          const [before, after] = [result.strain_before, result.strain_after];
          note(
            `Optimised ${result.atoms.length} atoms in ${result.iterations} steps` +
              (before !== null && after !== null ? `: strain ${before.toFixed(1)} → ${after.toFixed(1)}` : ""),
          );

          if (base.version === result.base_version) {
            adopt(patched(base, result.atoms, result));
          } else if (base.version !== result.version) {
            // the sparse result says nothing of an atom the client has moved since, so ask for the whole state
            socket.resync(base.session_id, base.version).then(adopt).catch((cause: unknown) => note(reason(cause), true));
          }
          break;
        }
        case "unchanged":
          note("The geometry is already as relaxed as it can be");
          break;
        case "failed":
          note(result.message ?? "The optimisation failed", true);
          break;
        default:
          note(result.message ?? "The optimisation was cancelled");
      }
    });

    void (async () => {
      try {
        await socket.connect();
      } catch (cause) {
        if (live) {
          setPhase({ kind: "failed", message: reason(cause) });
          note(reason(cause), true);
        }
        return;
      }

      if (!live) {
        return;
      }

      note("Connected");
      const wanted = requested();
      if (wanted.moleculeId) {
        await openStored(socket, wanted.moleculeId, wanted.smiles);
      } else if (wanted.smiles) {
        await openSmiles(socket, wanted.smiles);
      } else {
        setPhase({ kind: "wanted", message: "Enter a SMILES to start editing." });
      }
    })();

    return () => {
      live = false;
      hearGeometry();
      hearProgress();
      hearResult();
      socket.close();
      socketRef.current = null;
    };
  }, [adopt, note, openSmiles, openStored]);

  const submit = useCallback(
    (smiles: string) => {
      const socket = socketRef.current;
      if (socket) {
        void openSmiles(socket, smiles);
      }
    },
    [openSmiles],
  );

  const submitSmiles = useCallback(
    async (smiles: string): Promise<SubmitResult> => {
      const socket = socketRef.current;
      const base = held.current;
      if (!socket || !base) {
        return { kind: "refused", message: "There is no open session to edit." };
      }

      try {
        const reply = await socket.submitSmiles(base.session_id, base.version, smiles);
        if (reply.type === "conflict") {
          adopt(reply.state);
          note("The structure changed since you started editing, so your SMILES was not used", true);
          return { kind: "conflict" };
        }

        adopt(reply);
        describingSmiles(reply.smiles);
        note(`Structure replaced: ${reply.smiles}`);
        return { kind: "replaced" };
      } catch (cause) {
        note(reason(cause), true);
        return { kind: "refused", message: reason(cause) };
      }
    },
    [adopt, note],
  );

  /**
   * Runs edits one at a time, each on the version the one before left. Sent together, they
   * would all carry the same base version and every one after the first would be refused.
   */
  const serially = useCallback(<T,>(task: () => Promise<T>): Promise<T> => {
    const run = queue.current.then(task, task);
    queue.current = run.catch(() => undefined);
    return run;
  }, []);

  /**
   * Puts atoms at positions, in one patch.
   *
   * @param moves the atoms and where to put each
   * @param commit whether this ends the edit it belongs to; a patch that does not is part of a drag
   */
  const send = useCallback(
    (moves: AtomMove[], commit: boolean): Promise<EditResult> =>
      serially(async () => {
        const socket = socketRef.current;
        const base = held.current;
        if (!socket || !base) {
          return { kind: "refused", message: "There is no open session to edit." };
        }

        try {
          const reply = await socket.patchAtoms(base.session_id, base.version, moves, commit);
          if (reply.type === "conflict") {
            adopt(reply.state);
            note("The structure changed under your edit, so it was not applied", true);
            return { kind: "conflict" };
          }

          // a resync that landed meanwhile is authoritative, and already says where the atom is
          if (held.current?.version === base.version) {
            adopt(patched(base, moves, reply));
          }
          if (commit) {
            const [first] = moves as [AtomMove, ...AtomMove[]];
            const { x, y, z } = first.position;
            note(moves.length === 1 ? `Moved ${first.id} to ${x.toFixed(3)}, ${y.toFixed(3)}, ${z.toFixed(3)}` : `Moved ${moves.length} atoms`);
          }
          return { kind: "applied" };
        } catch (cause) {
          note(reason(cause), true);
          return { kind: "refused", message: reason(cause) };
        }
      }),
    [adopt, note, serially],
  );

  const moveAtom = useCallback((id: string, position: Vec3) => send([{ id, position }], true), [send]);

  const moveAtoms = useCallback((moves: AtomMove[]) => send(moves, true), [send]);

  /**
   * Puts the molecule at a pose.
   *
   * @param pose where to put it
   * @param commit whether this ends the edit it belongs to; a patch that does not is part of a drag
   */
  const sendPose = useCallback(
    (pose: Pose, commit: boolean): Promise<EditResult> =>
      serially(async () => {
        const socket = socketRef.current;
        const base = held.current;
        if (!socket || !base) {
          return { kind: "refused", message: "There is no open session to edit." };
        }

        try {
          const reply = await socket.transformMolecule(base.session_id, base.version, pose, commit);
          if (reply.type === "conflict") {
            adopt(reply.state);
            note("The structure changed under your edit, so it was not applied", true);
            return { kind: "conflict" };
          }

          // a resync that landed meanwhile is authoritative, and already says where the molecule is
          if (held.current?.version === base.version) {
            adopt({
              ...base,
              version: reply.version,
              can_undo: reply.can_undo,
              can_redo: reply.can_redo,
              saved_molecule_id: reply.saved_molecule_id,
              transform: pose,
            });
          }
          if (commit) {
            const { x, y, z } = pose.position;
            note(`Moved the molecule to ${x.toFixed(3)}, ${y.toFixed(3)}, ${z.toFixed(3)}`);
          }
          return { kind: "applied" };
        } catch (cause) {
          note(reason(cause), true);
          return { kind: "refused", message: reason(cause) };
        }
      }),
    [adopt, note, serially],
  );

  const moveMolecule = useCallback((pose: Pose) => sendPose(pose, true), [sendPose]);

  /**
   * Reports where something being dragged has got to. The first patch goes at once and the rest are spaced out, each
   * carrying whatever the latest report is by then.
   *
   * @param key what is being dragged, so that a report about something else starts a new drag
   * @param report where it is now
   * @param same whether two reports put it in the same place
   * @param transmit sends a report to the session, as part of the drag
   */
  const reporting = useCallback(<T,>(key: string, report: T, same: (first: T, second: T) => boolean, transmit: (report: T) => void) => {
    let current = drag.current;
    if (!current || current.key !== key) {
      current = { key, latest: report, sent: null, same, transmit, at: 0, timer: undefined, closed: false } as Drag;
      drag.current = current;
    }
    current.latest = report;

    if (current.timer !== undefined) {
      return;
    }

    const live = current;
    const wait = Math.max(0, live.at + DRAG_INTERVAL_MS - Date.now());
    live.timer = setTimeout(() => {
      live.timer = undefined;
      live.at = Date.now();
      if (live.closed || (live.sent !== null && (live.same as (a: unknown, b: unknown) => boolean)(live.sent, live.latest))) {
        return;
      }
      live.sent = live.latest;
      (live.transmit as (report: unknown) => void)(live.latest);
    }, wait);
  }, []);

  const dragAtoms = useCallback(
    (moves: AtomMove[]) => reporting(`atoms:${moves.map((move) => move.id).join(",")}`, moves, sameSpots, (latest) => void send(latest, false)),
    [reporting, send],
  );

  const dragMolecule = useCallback((pose: Pose) => reporting("molecule", pose, samePose, (latest) => void sendPose(latest, false)), [reporting, sendPose]);

  /** Ends the drag in progress, if there is one, so that nothing it still had waiting is sent. */
  const closeDrag = useCallback((): Drag | null => {
    const current = drag.current;
    drag.current = null;
    if (current) {
      clearTimeout(current.timer);
      current.closed = true;
    }
    return current;
  }, []);

  const dropAtoms = useCallback(
    (moves: AtomMove[]) => {
      closeDrag();
      return send(moves, true);
    },
    [closeDrag, send],
  );

  const cancelDrag = useCallback(
    async (from: AtomMove[]): Promise<EditResult> => {
      const ended = closeDrag();

      // a drag that had sent nothing has nothing to put back. One that had is put back by a patch that
      // commits, which records nothing to undo because the atoms end where they began
      return ended?.sent ? send(from, true) : { kind: "applied" };
    },
    [closeDrag, send],
  );

  const dropMolecule = useCallback(
    (pose: Pose) => {
      closeDrag();
      return sendPose(pose, true);
    },
    [closeDrag, sendPose],
  );

  const cancelMoleculeDrag = useCallback(
    async (from: Pose): Promise<EditResult> => {
      const ended = closeDrag();

      // as with atoms: only a drag that had sent something has anything to put back
      return ended?.sent ? sendPose(from, true) : { kind: "applied" };
    },
    [closeDrag, sendPose],
  );

  const lockAtoms = useCallback(
    (ids: string[], locked: boolean): Promise<EditResult> =>
      serially(async () => {
        const socket = socketRef.current;
        const base = held.current;
        if (!socket || !base) {
          return { kind: "refused", message: "There is no open session to edit." };
        }

        try {
          const reply = await socket.lockAtoms(base.session_id, base.version, ids, locked);
          if (reply.type === "conflict") {
            adopt(reply.state);
            note("The structure changed under your edit, so it was not applied", true);
            return { kind: "conflict" };
          }

          // a resync that landed meanwhile is authoritative, and already says what is locked
          if (held.current?.version === base.version) {
            adopt({ ...base, version: reply.version, locked: reply.locked, saved_molecule_id: reply.saved_molecule_id });
          }
          note(`${locked ? "Locked" : "Unlocked"} ${ids.length === 1 ? ids[0] : `${ids.length} atoms`}`);
          return { kind: "applied" };
        } catch (cause) {
          note(reason(cause), true);
          return { kind: "refused", message: reason(cause) };
        }
      }),
    [adopt, note, serially],
  );

  const step = useCallback(
    (direction: "undo" | "redo"): Promise<EditResult> =>
      serially(async () => {
        const socket = socketRef.current;
        const base = held.current;
        if (!socket || !base) {
          return { kind: "refused", message: "There is no open session to edit." };
        }

        try {
          const reply = await socket.step(direction, base.session_id, base.version);
          if (reply.type === "conflict") {
            adopt(reply.state);
            note(`The structure changed, so the ${direction} was not made`, true);
            return { kind: "conflict" };
          }

          // the answer is only the atoms that moved; a resync that landed meanwhile already says where they are
          if (held.current?.version === base.version) {
            adopt(patched(base, reply.atoms, reply, reply.transform));
          }
          note(direction === "undo" ? "Undid the last edit" : "Redid the last undone edit");
          return { kind: "applied" };
        } catch (cause) {
          note(reason(cause), true);
          return { kind: "refused", message: reason(cause) };
        }
      }),
    [adopt, note, serially],
  );

  const optimise = useCallback(
    (): Promise<EditResult> =>
      serially(async () => {
        const socket = socketRef.current;
        const base = held.current;
        if (!socket || !base) {
          return { kind: "refused", message: "There is no open session to optimise." };
        }

        // set before asking, because a small molecule can finish before the server's answer to the request arrives
        setOptimising({ iteration: 0 });
        setOutcome(null);
        try {
          const reply = await socket.optimise(base.session_id, base.version);
          if (reply.type === "conflict") {
            setOptimising(null);
            adopt(reply.state);
            note("The structure changed, so it was not optimised", true);
            return { kind: "conflict" };
          }

          note("Optimising");
          return { kind: "applied" };
        } catch (cause) {
          setOptimising(null);
          note(reason(cause), true);
          return { kind: "refused", message: reason(cause) };
        }
      }),
    [adopt, note, serially],
  );

  const cancelOptimise = useCallback(async (): Promise<void> => {
    const socket = socketRef.current;
    const base = held.current;
    if (!socket || !base) {
      return;
    }

    try {
      await socket.cancelOptimise(base.session_id);
    } catch (cause) {
      note(reason(cause), true);
    }
  }, [note]);

  const undo = useCallback(() => step("undo"), [step]);
  const redo = useCallback(() => step("redo"), [step]);

  return {
    phase,
    state,
    optimising,
    outcome,
    geometry,
    activity,
    openSmiles: submit,
    submitSmiles,
    moveAtom,
    moveAtoms,
    dragAtoms,
    dropAtoms,
    cancelDrag,
    moveMolecule,
    dragMolecule,
    dropMolecule,
    cancelMoleculeDrag,
    lockAtoms,
    optimise,
    cancelOptimise,
    undo,
    redo,
  };
}

/** Whether two reports put the same atoms in the same places. */
function sameSpots(first: AtomMove[], second: AtomMove[]): boolean {
  return first.every((move, i) => {
    const other = second[i];
    return other !== undefined && move.position.x === other.position.x && move.position.y === other.position.y && move.position.z === other.position.z;
  });
}

/**
 * The state after the session accepted a change, without waiting for it to send the whole state again.
 *
 * @param base the state the change was made on
 * @param moves the atoms that moved and where each went
 * @param ack the session's acknowledgement, which says the version and history it is now at
 * @param transform where the molecule now sits, if the change moved it as a whole
 */
function patched(base: SessionState, moves: { id: string; position: Vec3 }[], ack: PatchAck | EditApplied | OptimiseResult, transform: Pose | null = null): SessionState {
  const to = new Map(moves.map((move) => [move.id, move.position]));
  return {
    ...base,
    transform: transform ?? base.transform,
    version: ack.version,
    can_undo: ack.can_undo,
    can_redo: ack.can_redo,
    saved_molecule_id: ack.saved_molecule_id,
    atoms: base.atoms.map((atom) => {
      const position = to.get(atom.id);
      return position ? { ...atom, position } : atom;
    }),
  };
}

/** Whether the failure was the molecule itself being unusable, rather than the server or network. */
function refusedTheMolecule(cause: unknown): boolean {
  if (cause instanceof ApiError) {
    return cause.status === 400;
  }
  return cause instanceof StudioError && (cause.code === "UNKNOWN_MOLECULE" || cause.code === "INVALID_MESSAGE");
}

function reason(cause: unknown): string {
  return cause instanceof Error ? cause.message : String(cause);
}
