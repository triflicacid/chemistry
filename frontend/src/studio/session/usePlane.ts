import {useCallback, useEffect, useRef, useState} from "react";
import type {PlaneResult} from "./messages";
import {reason} from "./errors";
import type {SessionLink} from "./useSocket";
import {useWarner} from "./warnings";

export interface Plane {
  /** The electrostatic potential on a plane, as the server last said. */
  plane: PlaneResult | null;
  /** When the calculation under way was asked for, as {@link Date.now}, or null if there is none. */
  planeSince: number | null;
  /**
   * Asks for the potential on a plane across the session, abandoning any such request still being worked out.
   *
   * @param model the kind of Hartree-Fock calculation
   * @param fragment an atom of the molecule to calculate, or null for the whole session
   * @param multiplicity the spin multiplicity to calculate it at, or null for the lowest the electron count allows
   * @param axis the axis the plane is at right angles to
   * @param position how far along the box in that axis the plane is, from 0 to 1
   * @param automatic whether it is asked only because the session or the plane changed, so that an answer that says what the last did is not warned of again
   */
  requestPlane: (model: string, fragment: string | null, multiplicity: number | null, axis: "x" | "y" | "z", position: number, automatic?: boolean) => void;
}

export function usePlane({ socketRef, held, note, warn }: SessionLink): Plane {
  const [plane, setPlane] = useState<PlaneResult | null>(null);
  const [planeSince, setPlaneSince] = useState<number | null>(null);
  const warnOnce = useWarner(warn);
  const automatically = useRef(false);

  useEffect(() => {
    const socket = socketRef.current;
    if (!socket) {
      return;
    }

    return socket.on<PlaneResult>("plane.result", (result) => {
      if (held.current && held.current.session_id !== result.session_id) {
        return;
      }
      setPlaneSince(null);
      warnOnce(result.status === "complete" ? null : (result.message ?? "The potential on the plane could not be calculated."), automatically.current);
      // a result older than the one held is stale
      setPlane((before) => (before && before.session_id === result.session_id && before.version > result.version ? before : result));
    });
  }, [held, socketRef, warnOnce]);

  const requestPlane = useCallback(
    (model: string, fragment: string | null, multiplicity: number | null, axis: "x" | "y" | "z", position: number, automatic = false) => {
      automatically.current = automatic;
      const socket = socketRef.current;
      const base = held.current;
      if (!socket || !base) {
        return;
      }

      setPlaneSince(Date.now());
      socket.requestPlane(base.session_id, model, fragment, multiplicity, axis, position).catch((cause: unknown) => {
        setPlaneSince(null);
        note(reason(cause), true);
      });
    },
    [held, note, socketRef],
  );

  return { plane, planeSince, requestPlane };
}
