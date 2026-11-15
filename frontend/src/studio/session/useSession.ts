import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ChemistryApi } from "../../api/ChemistryApi";
import { ApiError } from "../../api/http";
import { describing, requested } from "./location";
import type { SessionState } from "./messages";
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

export interface Session {
  phase: SessionPhase;
  state: SessionState | null;
  activity: Activity[];
  /** Stores a molecule written as SMILES and opens a session on it. */
  openSmiles: (smiles: string) => void;
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

  return { phase, state, activity, openSmiles: submit };
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
