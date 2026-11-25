import {type RefObject, useCallback, useEffect, useRef, useState} from "react";
import type {ChemistryApi} from "../../api/ChemistryApi";
import {describing, requested} from "./location";
import type {GeometryResult, SessionState} from "./messages";
import {isUnknownMolecule, reason} from "./errors";
import {StudioError, StudioSocket} from "./studioSocket";
import type {Activity, SessionPhase} from "./types";

/** How many lines of activity are kept. */
const HISTORY = 200;

export interface SocketEvents {
  /** The session is a different one. */
  reset: () => void;
  /** The connection dropped. */
  dropped: () => void;
}

/** What the other session hooks need of the connection. */
export interface SessionLink {
  socketRef: RefObject<StudioSocket | null>;
  /** The latest state, which is ahead of `state` between a change and the render that shows it. */
  held: RefObject<SessionState | null>;
  adopt: (next: SessionState) => void;
  note: (message: string, failed?: boolean, preformatted?: boolean) => void;
  /** Says that what was asked for cannot be done, as a warning rather than an error. */
  warn: (message: string) => void;
}

export interface Socket extends SessionLink {
  phase: SessionPhase;
  state: SessionState | null;
  geometry: GeometryResult | null;
  activity: Activity[];
  /** Stores a molecule written as SMILES and opens a session on it. */
  openSmiles: (smiles: string) => void;
}

/**
 * Connects to the editing channel and opens whatever the URL asks for, keeping the state the
 * server sends. A molecule ID that no longer exists falls back to the SMILES beside it.
 *
 * @param api the client to store molecules with
 * @param events how the optimisation is told of what happens to the session
 */
export function useSocket(api: ChemistryApi, events: RefObject<SocketEvents>): Socket {
  const [phase, setPhase] = useState<SessionPhase>({ kind: "connecting" });
  const [state, setState] = useState<SessionState | null>(null);
  const [geometry, setGeometry] = useState<GeometryResult | null>(null);
  const [activity, setActivity] = useState<Activity[]>([]);

  const socketRef = useRef<StudioSocket | null>(null);
  const held = useRef<SessionState | null>(null);

  const note = useCallback((message: string, failed = false, preformatted = false) => {
    setActivity((before) => [...before, { at: Date.now(), message, failed, preformatted }].slice(-HISTORY));
  }, []);

  const warn = useCallback((message: string) => {
    setActivity((before) => [...before, { at: Date.now(), message, failed: false, warning: true }].slice(-HISTORY));
  }, []);

  const adopt = useCallback(
    (next: SessionState) => {
      // what was said of another session says nothing of this one
      if (held.current && held.current.session_id !== next.session_id) {
        setGeometry(null);
        events.current.reset();
      }
      // the molecule saved is what a reload should open, since it holds the edits and the molecule opened does not
      if (next.saved_molecule_id && next.saved_molecule_id !== held.current?.saved_molecule_id) {
        describing(next.saved_molecule_id, next.smiles);
      }
      held.current = next;
      setState(next);
      setPhase({ kind: "open" });
    },
    [events],
  );

  /** Stores a molecule and opens it. */
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
        setPhase(isUnknownMolecule(cause) ? { kind: "wanted", message } : { kind: "failed", message });
        // the server's SMILES errors point at the fault with a caret, which only lines up in a fixed-width font
        note(message, true, true);
      }
    },
    [adopt, api, note],
  );

  /** Opens a stored molecule, falling back to its SMILES. */
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

        // the first greeting is the one connect() resolves on; a later one means the socket dropped and came back
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
        events.current.dropped();
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
      socket.close();
      socketRef.current = null;
    };
  }, [adopt, events, note, openSmiles, openStored]);

  const submit = useCallback(
    (smiles: string) => {
      const socket = socketRef.current;
      if (socket) {
        void openSmiles(socket, smiles);
      }
    },
    [openSmiles],
  );

  return { phase, state, geometry, activity, socketRef, held, adopt, note, warn, openSmiles: submit };
}
