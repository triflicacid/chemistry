import {useCallback, useEffect, useRef, useState} from "react";
import type {ForcesResult} from "./messages";
import {reason} from "./errors";
import type {SessionLink} from "./useSocket";
import {useWarner} from "./warnings";

export interface Forces {
  /** The forces as the server last said, or null if it has not. They describe the version in `version`. */
  forces: ForcesResult | null;
  /** When the calculation under way was asked for, as {@link Date.now} says, or null if there is none. */
  calculatingSince: number | null;
  /**
   * Asks the server for the forces on the session as it is now. Several asks close together are answered once.
   *
   * @param automatic whether it is asked only because the session changed, so that an answer that says what the last did is not warned of again
   */
  requestForces: (automatic?: boolean) => void;
}

/**
 * Keeps the forces the server sends, and asks for them.
 *
 * @param link the connection
 */
export function useForces({ socketRef, held, note, warn }: SessionLink): Forces {
  const warnOnce = useWarner(warn);
  const automatically = useRef(false);
  const [forces, setForces] = useState<ForcesResult | null>(null);
  const [calculatingSince, setCalculatingSince] = useState<number | null>(null);

  useEffect(() => {
    const socket = socketRef.current;
    if (!socket) {
      return;
    }

    return socket.on<ForcesResult>("forces.result", (result) => {
      if (held.current && held.current.session_id !== result.session_id) {
        return;
      }
      setCalculatingSince(null);
      warnOnce(result.status === "complete" ? null : (result.message ?? "The forces could not be calculated."), automatically.current);
      // a result older than the one held is stale
      setForces((before) => (before && before.session_id === result.session_id && before.version > result.version ? before : result));
    });
  }, [held, socketRef, warnOnce]);

  const requestForces = useCallback((automatic = false) => {
    automatically.current = automatic;
    const socket = socketRef.current;
    const base = held.current;
    if (!socket || !base) {
      return;
    }

    setCalculatingSince(Date.now());
    socket.requestForces(base.session_id).catch((cause: unknown) => {
      setCalculatingSince(null);
      note(reason(cause), true);
    });
  }, [held, note, socketRef]);

  return { forces, calculatingSince, requestForces };
}
