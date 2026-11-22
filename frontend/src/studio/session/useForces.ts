import {useCallback, useEffect, useState} from "react";
import type {ForcesResult} from "./messages";
import {reason} from "./errors";
import type {SessionLink} from "./useSocket";
import {useWarnOnce} from "./warnings";

export interface Forces {
  /** The forces as the server last said, or null if it has not. They describe the version in `version`. */
  forces: ForcesResult | null;
  /** When the calculation under way was asked for, as {@link Date.now} says, or null if there is none. */
  calculatingSince: number | null;
  /** Asks the server for the forces on the session as it is now. Several asks close together are answered once. */
  requestForces: () => void;
}

/**
 * Keeps the forces the server sends, and asks for them.
 *
 * @param link the connection
 */
export function useForces({ socketRef, held, note, warn }: SessionLink): Forces {
  const warnOnce = useWarnOnce(warn);
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
      warnOnce(result.status === "complete" ? null : (result.message ?? "The forces could not be calculated."));
      // a result older than the one held is stale
      setForces((before) => (before && before.session_id === result.session_id && before.version > result.version ? before : result));
    });
  }, [held, socketRef, warnOnce]);

  const requestForces = useCallback(() => {
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
