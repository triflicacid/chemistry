import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ChemistryApi } from "../../api/ChemistryApi";
import { ApiError } from "../../api/http";
import { describing, describingSmiles, requested } from "./location";
import type { Vec3 } from "../../api/types/structure";
import type { PatchAck, SessionState } from "./messages";
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

  const note = useCallback((message: string, failed = false) => {
    setActivity((before) => [...before, { at: Date.now(), message, failed }].slice(-HISTORY));
  }, []);

  const adopt = useCallback((next: SessionState) => {
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

  const moveAtom = useCallback(
    (id: string, position: Vec3): Promise<EditResult> =>
      serially(async () => {
        const socket = socketRef.current;
        const base = held.current;
        if (!socket || !base) {
          return { kind: "refused", message: "There is no open session to edit." };
        }

        try {
          const reply = await socket.patchAtoms(base.session_id, base.version, [{ id, position }]);
          if (reply.type === "conflict") {
            adopt(reply.state);
            note("The structure changed under your edit, so it was not applied", true);
            return { kind: "conflict" };
          }

          // a resync that landed meanwhile is authoritative, and already says where the atom is
          if (held.current?.version === base.version) {
            adopt(patched(base, id, position, reply));
          }
          note(`Moved ${id} to ${position.x.toFixed(3)}, ${position.y.toFixed(3)}, ${position.z.toFixed(3)}`);
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

          adopt(reply);
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

  return { phase, state, activity, openSmiles: submit, submitSmiles, moveAtom, undo, redo };
}

/**
 * The state after the session accepted a move, without waiting for it to send the whole state again.
 *
 * @param base the state the move was made on
 * @param id the atom that moved
 * @param position where it went
 * @param ack the session's acknowledgement, which says the version and history it is now at
 */
function patched(base: SessionState, id: string, position: Vec3, ack: PatchAck): SessionState {
  return {
    ...base,
    version: ack.version,
    can_undo: ack.can_undo,
    can_redo: ack.can_redo,
    atoms: base.atoms.map((atom) => (atom.id === id ? { ...atom, position } : atom)),
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
