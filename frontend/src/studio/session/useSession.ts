import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ChemistryApi } from "../../api/ChemistryApi";
import { ApiError } from "../../api/http";
import { describing, describingSmiles, requested } from "./location";
import type { Vec3 } from "../../api/types/structure";
import type { AtomMove, EditApplied, PatchAck, SessionState } from "./messages";
import { StudioError, StudioSocket } from "./studioSocket";

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

/** Atoms being dragged, and how much of the drag the session has been told. */
interface Drag {
  /** Which atoms are in the drag, so that a report about different ones starts a new drag. */
  key: string;
  /** Where the pointer last put the atoms. */
  latest: AtomMove[];
  /** The positions of the last patch sent, so that one that would repeat it is not. */
  sent: AtomMove[] | null;
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

export interface Session {
  phase: SessionPhase;
  state: SessionState | null;
  activity: Activity[];
  /** Stores a molecule written as SMILES and opens a session on it. */
  openSmiles: (smiles: string) => void;
  /** Rebuilds the open session's structure from SMILES text. */
  submitSmiles: (smiles: string) => Promise<SubmitResult>;
  /** Puts an atom at a position, as one edit that can be undone. */
  moveAtom: (id: string, position: Vec3) => Promise<EditResult>;
  /**
   * Reports where dragged atoms have got to. Sends at most one patch per {@link DRAG_INTERVAL_MS}, of the latest
   * positions, none of which is yet an edit to undo.
   */
  dragAtoms: (moves: AtomMove[]) => void;
  /** Ends a drag at positions, as one edit that can be undone however many patches the drag sent. */
  dropAtoms: (moves: AtomMove[]) => Promise<EditResult>;
  /** Abandons a drag, putting the atoms back at the positions they were picked up from. */
  cancelDrag: (moves: AtomMove[]) => Promise<EditResult>;
  /** Locks or unlocks atoms, so that they refuse or accept edits. A lock is saved with the molecule but is not an edit, so it cannot be undone. */
  lockAtoms: (ids: string[], locked: boolean) => Promise<EditResult>;
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
  const [activity, setActivity] = useState<Activity[]>([]);

  const socketRef = useRef<StudioSocket | null>(null);
  const held = useRef<SessionState | null>(null);
  const queue = useRef<Promise<unknown>>(Promise.resolve());
  const drag = useRef<Drag | null>(null);

  const note = useCallback((message: string, failed = false) => {
    setActivity((before) => [...before, { at: Date.now(), message, failed }].slice(-HISTORY));
  }, []);

  const adopt = useCallback((next: SessionState) => {
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
      onDrop: (why) => note(`The connection dropped (${why}); reconnecting`, true),
    });
    socketRef.current = socket;

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

  const dragAtoms = useCallback(
    (moves: AtomMove[]) => {
      const key = moves.map((move) => move.id).join(",");
      let current = drag.current;
      if (!current || current.key !== key) {
        current = { key, latest: moves, sent: null, at: 0, timer: undefined, closed: false };
        drag.current = current;
      }
      current.latest = moves;

      if (current.timer !== undefined) {
        return;
      }

      // the first patch goes at once and the rest are spaced out, each carrying whatever the latest position is by then
      const live = current;
      const wait = Math.max(0, live.at + DRAG_INTERVAL_MS - Date.now());
      live.timer = setTimeout(() => {
        live.timer = undefined;
        live.at = Date.now();
        if (live.closed || (live.sent && sameSpots(live.sent, live.latest))) {
          return;
        }
        live.sent = live.latest;
        void send(live.latest, false);
      }, wait);
    },
    [send],
  );

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
            adopt(patched(base, reply.atoms, reply));
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

  const undo = useCallback(() => step("undo"), [step]);
  const redo = useCallback(() => step("redo"), [step]);

  return { phase, state, activity, openSmiles: submit, submitSmiles, moveAtom, dragAtoms, dropAtoms, cancelDrag, lockAtoms, undo, redo };
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
 */
function patched(base: SessionState, moves: { id: string; position: Vec3 }[], ack: PatchAck | EditApplied): SessionState {
  const to = new Map(moves.map((move) => [move.id, move.position]));
  return {
    ...base,
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
