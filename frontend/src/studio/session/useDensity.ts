import {useCallback, useEffect, useState} from "react";
import type {DensityResult} from "./messages";
import {reason} from "./errors";
import type {SessionLink} from "./useSocket";

export interface Density {
  /** The electron density. */
  density: DensityResult | null;
  /** When the calculation under way was asked for, as {@link Date.now}, or null if there is none. */
  densitySince: number | null;
  /**
   * @param model the kind of Hartree-Fock calculation
   * @param fragment an atom of the molecule to calculate, or null for the whole session
   */
  requestDensity: (model: string, fragment: string | null) => void;
}

export function useDensity({ socketRef, held, note }: SessionLink): Density {
  const [density, setDensity] = useState<DensityResult | null>(null);
  const [densitySince, setDensitySince] = useState<number | null>(null);

  useEffect(() => {
    const socket = socketRef.current;
    if (!socket) {
      return;
    }

    return socket.on<DensityResult>("density.result", (result) => {
      if (held.current && held.current.session_id !== result.session_id) {
        return;
      }
      setDensitySince(null);
      // a result older than the one held is stale
      setDensity((before) => (before && before.session_id === result.session_id && before.version > result.version ? before : result));
    });
  }, [held, socketRef]);

  const requestDensity = useCallback(
    (model: string, fragment: string | null) => {
      const socket = socketRef.current;
      const base = held.current;
      if (!socket || !base) {
        return;
      }

      setDensitySince(Date.now());
      socket.requestDensity(base.session_id, model, fragment).catch((cause: unknown) => {
        setDensitySince(null);
        note(reason(cause), true);
      });
    },
    [held, note, socketRef],
  );

  return { density, densitySince, requestDensity };
}
