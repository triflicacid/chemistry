import {useCallback, useEffect, useState} from "react";
import type {EspResult} from "./messages";
import {reason} from "./errors";
import type {SessionLink} from "./useSocket";

export interface Esp {
  /** The electron density with its electrostatic potential. */
  esp: EspResult | null;
  /** When the calculation under way was asked for, as {@link Date.now}, or null if there is none. */
  espSince: number | null;
  /**
   * @param model the kind of Hartree-Fock calculation
   * @param fragment an atom of the molecule to calculate, or null for the whole session
   */
  requestEsp: (model: string, fragment: string | null) => void;
}

export function useEsp({ socketRef, held, note }: SessionLink): Esp {
  const [esp, setEsp] = useState<EspResult | null>(null);
  const [espSince, setEspSince] = useState<number | null>(null);

  useEffect(() => {
    const socket = socketRef.current;
    if (!socket) {
      return;
    }

    return socket.on<EspResult>("esp.result", (result) => {
      if ((held.current && held.current.session_id !== result.session_id)) {
        return;
      }
      setEspSince(null);
      // a result older than the one held is stale
      setEsp((before) => (before && before.session_id === result.session_id && before.version > result.version ? before : result));
    });
  }, [held, socketRef]);

  const requestEsp = useCallback(
    (model: string, fragment: string | null) => {
      const socket = socketRef.current;
      const base = held.current;
      if (!socket || !base) {
        return;
      }

      setEspSince(Date.now());
      socket.requestEsp(base.session_id, model, fragment).catch((cause: unknown) => {
        setEspSince(null);
        note(reason(cause), true);
      });
    },
    [held, note, socketRef],
  );

  return { esp, espSince, requestEsp };
}
