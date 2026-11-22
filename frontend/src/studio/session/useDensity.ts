import {useCallback, useEffect, useState} from "react";
import type {DensityResult} from "./messages";
import {reason} from "./errors";
import type {StudioSocket} from "./studioSocket";
import type {SessionLink} from "./useSocket";
import {useWarnOnce} from "./warnings";

/** One density: the last the server sent, when it was asked for, and how to ask again. */
interface Held {
  result: DensityResult | null;
  /** When the calculation under way was asked for, as {@link Date.now}, or null if there is none. */
  since: number | null;
  request: (model: string, fragment: string | null, multiplicity: number | null) => void;
}

export interface Density {
  /** The electron density. */
  density: DensityResult | null;
  /** When the calculation under way was asked for, as {@link Date.now}, or null if there is none. */
  densitySince: number | null;
  /**
   * @param model the kind of Hartree-Fock calculation
   * @param fragment an atom of the molecule to calculate, or null for the whole session
   * @param multiplicity the spin multiplicity to calculate it at, or null for the lowest the electron count allows
   */
  requestDensity: (model: string, fragment: string | null, multiplicity: number | null) => void;
  /** The spin density: the electrons of spin up less those of spin down. */
  spin: DensityResult | null;
  spinSince: number | null;
  requestSpin: (model: string, fragment: string | null, multiplicity: number | null) => void;
  /** The difference density: everything together less its molecules each on its own. */
  difference: DensityResult | null;
  differenceSince: number | null;
  requestDifference: (model: string, fragment: string | null, multiplicity: number | null) => void;
}

/**
 * Keeps one kind of density the server sends.
 *
 * @param link the connection to the session
 * @param type the type of message the density comes in
 * @param ask sends the request for it
 */
function useHeld({ socketRef, held, note, warn }: SessionLink, type: DensityResult["type"], ask: (socket: StudioSocket, sessionId: string, model: string, fragment: string | null, multiplicity: number | null) => Promise<unknown>): Held {
  const [result, setResult] = useState<DensityResult | null>(null);
  const [since, setSince] = useState<number | null>(null);
  const warnOnce = useWarnOnce(warn);

  useEffect(() => {
    const socket = socketRef.current;
    if (!socket) {
      return;
    }

    return socket.on<DensityResult>(type, (arrived) => {
      if (held.current && held.current.session_id !== arrived.session_id) {
        return;
      }
      setSince(null);
      warnOnce(arrived.status === "complete" ? null : (arrived.message ?? "The density could not be calculated."));
      // a result older than the one held is stale
      setResult((before) => (before && before.session_id === arrived.session_id && before.version > arrived.version ? before : arrived));
    });
  }, [held, socketRef, type, warnOnce]);

  const request = useCallback(
    (model: string, fragment: string | null, multiplicity: number | null) => {
      const socket = socketRef.current;
      const base = held.current;
      if (!socket || !base) {
        return;
      }

      setSince(Date.now());
      ask(socket, base.session_id, model, fragment, multiplicity).catch((cause: unknown) => {
        setSince(null);
        note(reason(cause), true);
      });
    },
    // the way of asking is fixed for each kind
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [held, note, socketRef],
  );

  return { result, since, request };
}

export function useDensity(link: SessionLink): Density {
  const density = useHeld(link, "density.result", (socket, id, model, fragment, multiplicity) => socket.requestDensity(id, model, fragment, multiplicity));
  const spin = useHeld(link, "spin.result", (socket, id, model, fragment, multiplicity) => socket.requestSpin(id, model, fragment, multiplicity));
  const difference = useHeld(link, "difference.result", (socket, id, model, fragment, multiplicity) => socket.requestDifference(id, model, fragment, multiplicity));

  return {
    density: density.result,
    densitySince: density.since,
    requestDensity: density.request,
    spin: spin.result,
    spinSince: spin.since,
    requestSpin: spin.request,
    difference: difference.result,
    differenceSince: difference.since,
    requestDifference: difference.request,
  };
}
