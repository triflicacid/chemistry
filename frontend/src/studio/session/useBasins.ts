import {useCallback, useEffect, useRef, useState} from "react";
import type {BasinsResult} from "./messages";
import {reason} from "./errors";
import type {SessionLink} from "./useSocket";
import {useWarner} from "./warnings";

export interface Basins {
  /** The electron localisation function split into its basins, which are its lone pairs and bonds. */
  basins: BasinsResult | null;
  /** When the calculation under way was asked for, as {@link Date.now}, or null if there is none. */
  basinsSince: number | null;
  /**
   * @param model the kind of Hartree-Fock calculation
   * @param fragment an atom of the molecule to calculate, or null for the whole session
   * @param multiplicity the spin multiplicity to calculate it at, or null for the lowest the electron count allows
   * @param automatic whether it is asked only because the session changed, so that an answer that says what the last did is not warned of again
   */
  requestBasins: (model: string, fragment: string | null, multiplicity: number | null, automatic?: boolean) => void;
}

export function useBasins({ socketRef, held, note, warn }: SessionLink): Basins {
  const warnOnce = useWarner(warn);
  const automatically = useRef(false);
  const [basins, setBasins] = useState<BasinsResult | null>(null);
  const [basinsSince, setBasinsSince] = useState<number | null>(null);

  useEffect(() => {
    const socket = socketRef.current;
    if (!socket) {
      return;
    }

    return socket.on<BasinsResult>("basins.result", (result) => {
      if (held.current && held.current.session_id !== result.session_id) {
        return;
      }
      setBasinsSince(null);
      warnOnce(result.status === "complete" ? null : (result.message ?? "The electron pairs could not be calculated."), automatically.current);
      // a result older than the one held is stale
      setBasins((before) => (before && before.session_id === result.session_id && before.version > result.version ? before : result));
    });
  }, [held, socketRef, warnOnce]);

  const requestBasins = useCallback(
    (model: string, fragment: string | null, multiplicity: number | null, automatic = false) => {
      automatically.current = automatic;
      const socket = socketRef.current;
      const base = held.current;
      if (!socket || !base) {
        return;
      }

      setBasinsSince(Date.now());
      socket.requestBasins(base.session_id, model, fragment, multiplicity).catch((cause: unknown) => {
        setBasinsSince(null);
        note(reason(cause), true);
      });
    },
    [held, note, socketRef],
  );

  return { basins, basinsSince, requestBasins };
}
