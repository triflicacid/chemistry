import {useCallback, useEffect, useRef, useState} from "react";
import type {NciResult} from "./messages";
import {reason} from "./errors";
import type {SessionLink} from "./useSocket";
import {useWarner} from "./warnings";

export interface Nci {
  /** The reduced density gradient and the signed density. */
  nci: NciResult | null;
  /** When the calculation under way was asked for, as {@link Date.now}, or null if there is none. */
  nciSince: number | null;
  /**
   * @param model the kind of Hartree-Fock calculation
   * @param fragment an atom of the molecule to calculate, or null for the whole session
   * @param multiplicity the spin multiplicity to calculate it at, or null for the lowest the electron count allows
   * @param automatic whether it is asked only because the session changed, so that an answer that says what the last did is not warned of again
   */
  requestNci: (model: string, fragment: string | null, multiplicity: number | null, automatic?: boolean) => void;
}

export function useNci({ socketRef, held, note, warn }: SessionLink): Nci {
  const warnOnce = useWarner(warn);
  const automatically = useRef(false);
  const [nci, setNci] = useState<NciResult | null>(null);
  const [nciSince, setNciSince] = useState<number | null>(null);

  useEffect(() => {
    const socket = socketRef.current;
    if (!socket) {
      return;
    }

    return socket.on<NciResult>("nci.result", (result) => {
      if (held.current && held.current.session_id !== result.session_id) {
        return;
      }
      setNciSince(null);
      warnOnce(result.status === "complete" ? null : (result.message ?? "The non-covalent contacts could not be calculated."), automatically.current);
      // a result older than the one held is stale
      setNci((before) => (before && before.session_id === result.session_id && before.version > result.version ? before : result));
    });
  }, [held, socketRef, warnOnce]);

  const requestNci = useCallback(
    (model: string, fragment: string | null, multiplicity: number | null, automatic = false) => {
      automatically.current = automatic;
      const socket = socketRef.current;
      const base = held.current;
      if (!socket || !base) {
        return;
      }

      setNciSince(Date.now());
      socket.requestNci(base.session_id, model, fragment, multiplicity).catch((cause: unknown) => {
        setNciSince(null);
        note(reason(cause), true);
      });
    },
    [held, note, socketRef],
  );

  return { nci, nciSince, requestNci };
}
